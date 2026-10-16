package com.irvin.inventory.sale;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.config.StoreProperties;
import com.irvin.inventory.sale.dto.CashDrawerResponse;
import com.irvin.inventory.sale.dto.CloseCashDrawerRequest;
import com.irvin.inventory.sale.dto.OpenCashDrawerRequest;
import com.irvin.inventory.security.CurrentUser;
import com.irvin.inventory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// One drawer per user and local day. Sales and cancellations lock it FOR SHARE and closing locks it
// FOR UPDATE, so the expected cash includes every sale and cancellation recorded before the closing.
@Service
@RequiredArgsConstructor
public class CashDrawerService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "businessDate", "id");
    private static final int MONEY_SCALE = 2;

    private final CashDrawerRepository cashDrawerRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;
    private final StoreProperties storeProperties;
    private final Clock clock;

    // Opens the user's drawer for today in the store's time zone.
    @Transactional
    public CashDrawerResponse open(OpenCashDrawerRequest request, Long userId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate today = LocalDate.ofInstant(now, storeProperties.timeZone());
        Optional<CashDrawer> openDrawer = cashDrawerRepository.findByUserIdAndClosedAtIsNull(userId);
        if (openDrawer.isPresent()) {
            LocalDate openDay = openDrawer.get().getBusinessDate();
            throw new ConflictException(openDay.equals(today)
                    ? "Your cash drawer is already open"
                    : "Close your cash drawer of " + openDay + " first");
        }
        if (cashDrawerRepository.existsByUserIdAndBusinessDate(userId, today)) {
            throw new ConflictException("Today's cash drawer is already closed");
        }
        // A concurrent open fails on uk_cash_drawer_user_date or uk_cash_drawer_open_user (409).
        CashDrawer drawer = cashDrawerRepository.saveAndFlush(CashDrawer.open(
                userRepository.getReferenceById(userId), today, request.openingFloat().setScale(MONEY_SCALE), now));
        return CashDrawerResponse.from(drawer);
    }

    // Part of the sale transaction: the seller's drawer for the sale's local day must be open.
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireOpenForSale(Long userId, Instant saleDate) {
        LocalDate day = LocalDate.ofInstant(saleDate, storeProperties.timeZone());
        CashDrawer drawer = cashDrawerRepository.findByUserIdAndBusinessDateForShare(userId, day)
                .orElseThrow(() -> new ConflictException("Open your cash drawer before selling"));
        if (!drawer.isOpen()) {
            throw new ConflictException("Your cash drawer for today is closed");
        }
    }

    // Part of the cancellation transaction: waits for a closing of the seller's drawer in progress.
    // A missing or closed drawer doesn't block the refund; it then shows only in the cash closing.
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForCancellation(Long sellerId, Instant at) {
        LocalDate day = LocalDate.ofInstant(at, storeProperties.timeZone());
        cashDrawerRepository.findByUserIdAndBusinessDateForShare(sellerId, day);
    }

    // The owner or an admin closes it. Expected cash is computed after the lock, once every sale and
    // cancellation holding the drawer has committed; past days can be closed late.
    @Transactional
    public CashDrawerResponse close(Long id, CloseCashDrawerRequest request, CurrentUser actor) {
        // Owner first, without a lock (a projection, so no stale entity stays in the persistence
        // context): probing other users' ids must not block their sales.
        cashDrawerRepository.findUserIdById(id)
                .filter(ownerId -> actor.isManager() || ownerId.equals(actor.id()))
                .orElseThrow(CashDrawerService::notFound);
        CashDrawer drawer = cashDrawerRepository.findByIdForUpdate(id).orElseThrow(CashDrawerService::notFound);
        if (!drawer.isOpen()) {
            throw new ConflictException("Cash drawer is already closed");
        }
        ZoneId zone = storeProperties.timeZone();
        LocalDate day = drawer.getBusinessDate();
        BigDecimal netCash = saleRepository.netCash(drawer.getUser().getId(), day, day.plusDays(1), zone.getId());
        drawer.close(
                userRepository.getReferenceById(actor.id()),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                drawer.getOpeningFloat().add(netCash).setScale(MONEY_SCALE),
                request.countedCash().setScale(MONEY_SCALE),
                request.note());
        return CashDrawerResponse.from(drawer);
    }

    @Transactional(readOnly = true)
    public CashDrawerResponse findCurrent(Long userId) {
        return cashDrawerRepository.findByUserIdAndClosedAtIsNull(userId)
                .map(CashDrawerResponse::from)
                .orElseThrow(() -> new NotFoundException("No open cash drawer"));
    }

    // A seller asking for another user's drawer gets 404, like sales.
    @Transactional(readOnly = true)
    public CashDrawerResponse findById(Long id, CurrentUser viewer) {
        return cashDrawerRepository.findDetailedById(id)
                .filter(found -> isVisibleTo(found, viewer))
                .map(CashDrawerResponse::from)
                .orElseThrow(CashDrawerService::notFound);
    }

    @Transactional(readOnly = true)
    public Page<CashDrawerResponse> search(CashDrawerSearch search, Pageable pageable, CurrentUser viewer) {
        CashDrawerSearch scoped = viewer.isManager() ? search : search.withUser(viewer.id());
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return cashDrawerRepository.findAll(scoped.toSpecification(), sorted).map(CashDrawerResponse::from);
    }

    private static boolean isVisibleTo(CashDrawer drawer, CurrentUser viewer) {
        return viewer.isManager() || drawer.getUser().getId().equals(viewer.id());
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Cash drawer not found");
    }
}
