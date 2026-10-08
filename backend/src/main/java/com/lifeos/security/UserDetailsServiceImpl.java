package com.lifeos.security;

import com.lifeos.entity.User;
import com.lifeos.entity.enums.Role;
import com.lifeos.entity.enums.UserStatus;
import com.lifeos.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
        return toPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserPrincipal loadById(String id) throws UsernameNotFoundException {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new UsernameNotFoundException("Account no longer exists"));
        return toPrincipal(user);
    }

    public static UserPrincipal toPrincipal(User user) {
        Role role = user.getRole() == null ? Role.USER : user.getRole();
        boolean enabled = user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null;
        return new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(), role, user.isEmailVerified(), enabled);
    }
}