package com.exam.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.exam.repo.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import com.exam.service.UserService;
import com.exam.model.User;
import com.exam.model.Role;
import com.exam.model.UserRole;
import java.util.HashSet;
import java.util.Set;
import com.exam.helper.UserFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.stream.Collectors;

@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, Model model) {
        // Validate credentials
        var user = userRepository.findByUsername(username);
        if (user == null || !bCryptPasswordEncoder.matches(password, user.getPassword())) {
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
        
        // Check if user has ADMIN role
        boolean isAdmin = user.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equalsIgnoreCase("ADMIN"));
        if (!isAdmin) {
            model.addAttribute("error", "You are not authorized to log in as admin.");
            return "login";
        }
        
        // Create authentication token and set it in SecurityContext
        Set<SimpleGrantedAuthority> authorities = user.getUserRoles().stream()
            .map(userRole -> new SimpleGrantedAuthority(userRole.getRole().getRoleName()))
            .collect(Collectors.toSet());
        
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            user.getUsername(), 
            null, 
            authorities
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        logger.info("Admin logged in successfully: {}", username);
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String email, @RequestParam String password, @RequestParam String firstName, @RequestParam String lastName, Model model) {
        logger.info("Attempting to register user: {}", username);
        try {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(bCryptPasswordEncoder.encode(password));
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setProfile("default.png");
            user.setEnabled(true);

            Set<UserRole> roles = new HashSet<>();
            Role role = new Role();
            role.setRoleId(46L);
            role.setRoleName("NORMAL");
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            roles.add(userRole);

            userService.createUser(user, roles);
            logger.info("User registered successfully: {}", username);
            return "redirect:/studentlogin";
        } catch (UserFoundException e) {
            logger.warn("Registration failed: username already exists: {}", username);
            model.addAttribute("error", "Username already exists. Please choose another.");
            return "register";
        } catch (Exception e) {
            logger.error("Registration failed for user: {}. Error: {}", username, e.getMessage(), e);
            model.addAttribute("error", "Registration failed. Please try again.");
            return "register";
        }
    }

    @GetMapping("/logout")
    public String logout() {
        // Invalidate session or token if needed
        return "redirect:/login";
    }

    @GetMapping("/test")
    public String test() {
        return "Test page is working!";
    }

    @GetMapping("/asklogin")
    public String askLoginPage() {
        return "asklogin";
    }

    @GetMapping("/studentlogin")
    public String studentLoginPage() {
        return "studentlogin";
    }

    @PostMapping("/studentlogin")
    public String studentLogin(@RequestParam String username, @RequestParam String password, Model model) {
        // Validate credentials
        var user = userRepository.findByUsername(username);
        if (user == null || !bCryptPasswordEncoder.matches(password, user.getPassword())) {
            model.addAttribute("error", "Invalid username or password");
            return "studentlogin";
        }
        
        // Check if user has NORMAL role
        boolean isNormal = user.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equalsIgnoreCase("NORMAL"));
        if (!isNormal) {
            model.addAttribute("error", "You are not authorized to log in as student.");
            return "studentlogin";
        }
        
        // Create authentication token and set it in SecurityContext
        Set<SimpleGrantedAuthority> authorities = user.getUserRoles().stream()
            .map(userRole -> new SimpleGrantedAuthority(userRole.getRole().getRoleName()))
            .collect(Collectors.toSet());
        
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            user.getUsername(), 
            null, 
            authorities
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        logger.info("Student logged in successfully: {}", username);
        return "redirect:/user/dashboard/" + username;
    }

    @GetMapping("/registernewstudent")
    public String registerNewStudentPage() {
        return "registernewstudent";
    }

    @PostMapping("/registernewstudent")
    public String registerNewStudent(@RequestParam String username, @RequestParam String email, @RequestParam String password, @RequestParam String firstName, @RequestParam String lastName, Model model) {
        logger.info("Admin registering new student: {}", username);
        try {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(bCryptPasswordEncoder.encode(password));
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setProfile("default.png");
            user.setEnabled(true);

            Set<UserRole> roles = new HashSet<>();
            Role role = new Role();
            role.setRoleId(46L);
            role.setRoleName("NORMAL");
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            roles.add(userRole);

            userService.createUser(user, roles);
            logger.info("Student registered successfully: {}", username);
            return "redirect:/admin/users";
        } catch (UserFoundException e) {
            logger.warn("Registration failed: username already exists: {}", username);
            model.addAttribute("error", "Username already exists. Please choose another.");
            return "registernewstudent";
        } catch (Exception e) {
            logger.error("Registration failed for student: {}. Error: {}", username, e.getMessage(), e);
            model.addAttribute("error", "Registration failed. Please try again.");
            return "registernewstudent";
        }
    }

    @GetMapping("/")
    public String redirectToAskLogin() {
        return "redirect:/asklogin";
    }
} 