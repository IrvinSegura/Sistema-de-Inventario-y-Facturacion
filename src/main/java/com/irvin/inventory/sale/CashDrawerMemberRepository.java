package com.irvin.inventory.sale;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.irvin.inventory.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CashDrawerMemberRepository extends JpaRepository<CashDrawerMember, CashDrawerMember.Key> {

    // Members never change once the drawer is open, so reading them without a lock is safe.
    @Query("select m.cashDrawer.id from CashDrawerMember m where m.user.id = :userId and m.businessDate = :day")
    Optional<Long> findDrawerIdByUserAndDay(Long userId, LocalDate day);

    @Query("select m.cashDrawer.id from CashDrawerMember m where m.user.id = :userId and m.drawerOpen = true")
    Optional<Long> findOpenDrawerIdByUser(Long userId);

    // The open drawer (any day) or today's drawer of each user; at most two rows per user.
    @Query("""
            select m from CashDrawerMember m join fetch m.user
            where m.user.id in :userIds and (m.drawerOpen = true or m.businessDate = :today)""")
    List<CashDrawerMember> findOpenOrOnDay(Collection<Long> userIds, LocalDate today);

    boolean existsByCashDrawerIdAndUserId(Long cashDrawerId, Long userId);

    @Query("""
            select m from CashDrawerMember m join fetch m.user
            where m.cashDrawer.id in :drawerIds
            order by m.user.name, m.user.id""")
    List<CashDrawerMember> findWithUserByDrawerIds(Collection<Long> drawerIds);

    // Active users who can still be assigned a drawer today.
    @Query("""
            select u from User u
            where u.active = true
              and not exists (select 1 from CashDrawerMember m
                              where m.user = u and (m.drawerOpen = true or m.businessDate = :today))
            order by u.name, u.id""")
    List<User> findAssignableUsers(LocalDate today);
}
