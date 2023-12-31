package com.needus.ecommerce.service.product.impl;

import com.needus.ecommerce.entity.product.Coupon;
import com.needus.ecommerce.entity.user.Cart;
import com.needus.ecommerce.entity.user.UserInformation;
import com.needus.ecommerce.repository.product.CouponRepository;
import com.needus.ecommerce.service.product.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {
    @Autowired
    CouponRepository couponRepository;
    @Override
    public float couponDiscount(Cart cart) {
        if(Objects.nonNull(cart.getCoupon())){
            return cart.getCoupon().getCouponDiscount();
        }
        return 0;
    }

    @Override
    public List<Coupon> findAllNonDeletedCoupons() {
        return couponRepository.findByIsDeletedFalseAndIsValidTrue();
    }

    @Override
    public boolean couponNameExists(String couponName) {
        return couponRepository.existsByCouponNameAndIsDeletedIsFalse(couponName);
    }
    @Override
    public boolean couponCodeExists(String couponCode) {
        return couponRepository.existsByCouponCodeAndIsDeletedIsFalse(couponCode);
    }
    @Override
    public void addCoupon(Coupon coupon) {
        coupon.setCouponDiscount(coupon.getCouponDiscount()/100);
        coupon.setCreatedDate(LocalDate.now());
        log.info("coupon Code : "+coupon.getCouponCode());
        couponRepository.save(coupon);
    }
    @Override
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId).get();
        coupon.setDeleted(true);
        couponRepository.save(coupon);
    }

    @Override
    public Coupon findById(Long couponId) {
        return couponRepository.findById(couponId).get();
    }

    @Override
    public List<Coupon> findAllUserUsedCoupon(UserInformation user) {
        List<Coupon> usedCoupons = new LinkedList<>();
        user.getUserOrders().forEach( order ->
        {
            if(Objects.nonNull(order.getCoupon()))
                usedCoupons.add(order.getCoupon());
        });
        return usedCoupons;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * ?")
    public void checkAndUpdateCouponValidity(){
        List<Coupon> coupons = couponRepository.findByIsValidTrueAndExpirationDateBefore(LocalDate.now());
        for (Coupon coupon : coupons) {
            coupon.setValid(false);
            couponRepository.save(coupon);
        }
        log.info("Coupon Validity Checked");
    }
}
