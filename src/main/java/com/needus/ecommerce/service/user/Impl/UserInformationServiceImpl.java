package com.needus.ecommerce.service.user.Impl;

import com.needus.ecommerce.entity.user.Cart;
import com.needus.ecommerce.entity.user.Wallet;
import com.needus.ecommerce.entity.user.Wishlist;
import com.needus.ecommerce.entity.user.enums.Role;
import com.needus.ecommerce.entity.user.UserInformation;
import com.needus.ecommerce.exceptions.TechnicalIssueException;
import com.needus.ecommerce.repository.user.ConfirmationTokenRepository;
import com.needus.ecommerce.repository.user.UserInformationRepository;
import com.needus.ecommerce.service.user.CartService;
import com.needus.ecommerce.service.user.UserInformationService;
import com.needus.ecommerce.service.user.WalletService;
import com.needus.ecommerce.service.user.WishlistService;
import com.needus.ecommerce.service.verification.ConfirmationTokenService;
import com.needus.ecommerce.service.verification.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


@Service
@Slf4j
public class UserInformationServiceImpl implements UserInformationService {
    private final ConfirmationTokenService confirmationTokenService;
    private final BCryptPasswordEncoder encoder;
    private final UserInformationRepository userRepository;
    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final EmailService emailService;

    private final SessionRegistry sessionRegistry;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final WalletService walletService;
    private final UserDetailsService userDetailsService;
    @Autowired
    public UserInformationServiceImpl(
        BCryptPasswordEncoder encoder, UserInformationRepository userRepository,
        ConfirmationTokenRepository confirmationTokenRepository, ConfirmationTokenService confirmationTokenService,
        EmailService emailService, SessionRegistry sessionRegistry, CartService cartService, WishlistService wishlistService, WalletService walletService, UserDetailsService userDetailsService){
        this.encoder = encoder;
        this.userRepository = userRepository;
        this.confirmationTokenRepository = confirmationTokenRepository;
        this.confirmationTokenService = confirmationTokenService;
        this.emailService = emailService;
        this.sessionRegistry = sessionRegistry;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.walletService = walletService;
        this.userDetailsService = userDetailsService;
    }


    @Override
    @Transactional
    public UserInformation register(UserInformation user) {
        Wishlist wishlist =  new Wishlist();
        Cart cart = cartService.createCart();
        wishlistService.createWishlist(wishlist);
        user.setPassword(encoder.encode(user.getPassword()));
        user.setEnabled(false);
        user.setUserWishlist(wishlist);
        user.setCart(cart);
        if(userRepository.count()<1){
            user.setRole(Role.ADMIN);
        }
        else {
            user.setRole(Role.USER);
        }
        UserInformation userInformation = save(user);
        Wallet wallet  = walletService.createWallet(user);//Creates wallet for the user
        user.setWallet(wallet);
        Optional<UserInformation> saved = Optional.of(save(user));
        saved.ifPresent( mail -> {
            //sends mail to the user's email for confirmation
                try {
                    String token = UUID.randomUUID().toString();
                    confirmationTokenService.save(saved.get(),token);
                    emailService.sendHtmlMail(mail);
                } catch (Exception e) {
                    log.error("Something went wrong while sending user confirmation token");
                    e.printStackTrace();
                }
            });
        return saved.get();
    }

    @Override
    public UserInformation findUserById(UUID id) {
        return userRepository.findById(id).get();
    }

    @Override
    public void blockUser(UUID id, HttpServletRequest request, HttpServletResponse response) {
        UserInformation user = userRepository.findById(id).get();
        List<Object> allPrincipals = sessionRegistry.getAllPrincipals();
        log.info(""+allPrincipals);
        for (Object principal : allPrincipals) {
            if (principal instanceof UserDetails otherUserDetails) {
                if (otherUserDetails.getUsername().equals(user.getUsername())) {
                    // Logging out the logged-in user
                    List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                    for (SessionInformation session : sessions) {
                        session.expireNow();
                    }
                }
            }
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Override
    public void deleteUserById(UUID id) {
        UserInformation user = userRepository.findById(id).get();
        user.setDeleted(true);
        userRepository.save(user);
    }

    @Override
    public void updateUser(UUID id, UserInformation user) {
        UserInformation updateInfo = userRepository.findById(id).get();
        if(!user.getUsername().equals(updateInfo.getUsername())||!user.getUsername().isEmpty()){
            updateInfo.setUsername(user.getUsername());
        }
        if(!user.getEmail().equals(updateInfo.getEmail())||!user.getEmail().isEmpty()){
            updateInfo.setEmail(user.getEmail());
        }
        if(Objects.nonNull(user.getPhoneNumber())&&!user.getPhoneNumber().equals(updateInfo.getPhoneNumber())){
            updateInfo.setPhoneNumber(user.getPhoneNumber());
        }
        userRepository.save(updateInfo);
    }

    @Override
    public UserInformation findUserByName(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public List<UserInformation> findAllUsers() {
        return userRepository.findAllNonDeleted();
    }

    @Override
    public boolean usersExistsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public UserInformation save(UserInformation user) {
        user.setUserCreatedAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
        return userRepository.save(user);
    }


    @Override
    public UserInformation getCurrentUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByUsername(authentication.getName());
    }

    @Override
    public void changePassword(UserInformation user, String password) {
        user.setPassword(encoder.encode(password));
        userRepository.save(user);
    }

    @Override
    public boolean usersExistsByUserId(UUID userId) {
        return userRepository.existsByUserIdAndIsDeletedFalseAndIsEnabledTrue(userId);
    }

    @Override
    public boolean userExistsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void editUserDetails(UUID userId, String username, String email, String phoneNumber) {
        try{
           UserInformation user = findUserById(userId);
           if(!user.getEmail().equals(username)||
               !username.replaceAll("\\s", "").equals("")){
               user.setUsername(username);
           }
            if(!user.getEmail().equals(email)||
                !email.replaceAll("\\s", "").equals("")){
                user.setEmail(email);
            }
            if(!user.getPhoneNumber().equals(phoneNumber)||
                !phoneNumber.replaceAll("\\s", "").equals("")){
                user.setPhoneNumber(phoneNumber);
            }
            UserInformation updatedUser = userRepository.save(user);
            UserDetails updatedUserDetails =
                userDetailsService.loadUserByUsername(updatedUser.getUsername());
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if(authentication instanceof OAuth2AuthenticationToken){
                return;
            }
            UsernamePasswordAuthenticationToken updatedAuth =
                new UsernamePasswordAuthenticationToken(updatedUserDetails,null,updatedUserDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(updatedAuth);
        }
        catch (Exception e){
            log.error("Something went wrong while editing the user");
            throw new TechnicalIssueException("Something went wrong while editing the user");
        }
    }
}
