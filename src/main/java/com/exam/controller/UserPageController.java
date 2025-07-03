package com.exam.controller;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.exam.repo.QuizRepository;
import com.exam.repo.QuestionRepository;
import com.exam.repo.UserRepository;
import com.exam.repo.UserQuizResultRepository;
import com.exam.repo.RoleRepository;
import com.exam.model.exam.Quiz;
import com.exam.model.exam.Question;
import com.exam.model.User;
import com.exam.model.UserQuizResult;
import com.exam.model.QuizResultDTO;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserPageController {

    @Autowired
    private QuizRepository quizRepository;
    
    @Autowired
    private QuestionRepository questionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserQuizResultRepository userQuizResultRepository;

    @Autowired
    private RoleRepository roleRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpServletRequest request) {
        // Check authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || 
            "anonymousUser".equals(authentication.getName())) {
            return "redirect:/studentlogin?error=Please login to access dashboard";
        }
        
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);
        
        if (user == null) {
            return "redirect:/studentlogin?error=User not found. Please login again.";
        }
        
        System.out.println("=== User Dashboard Debug ===");
        System.out.println("Authenticated User: " + user.getUsername() + " (ID: " + user.getId() + ")");
        
        // Get all started quizzes
        List<Quiz> allQuizzes = quizRepository.findAll().stream()
            .filter(Quiz::isStarted)
            .collect(Collectors.toList());
        System.out.println("Total started quizzes: " + allQuizzes.size());
        
        // Get quizzes this user has already attempted
        List<UserQuizResult> userResults = userQuizResultRepository.findByUser(user);
        Set<Long> attemptedQuizIds = userResults.stream()
            .map(result -> result.getQuiz().getqId())
            .collect(Collectors.toSet());
        
        System.out.println("User has attempted " + attemptedQuizIds.size() + " quizzes");
        
        // Filter out quizzes the user has already attempted
        List<Quiz> availableQuizzes = allQuizzes.stream()
            .filter(quiz -> !attemptedQuizIds.contains(quiz.getqId()))
            .collect(Collectors.toList());
        
        System.out.println("Available quizzes for user: " + availableQuizzes.size());
        System.out.println("=== End Dashboard Debug ===");
        
        model.addAttribute("user", user);
        model.addAttribute("quizzes", availableQuizzes);
        
        // Add success/error messages from query parameters
        String success = request.getParameter("success");
        String error = request.getParameter("error");
        if (success != null) {
            model.addAttribute("successMessage", success);
        }
        if (error != null) {
            model.addAttribute("errorMessage", error);
        }
        
        return "user_dashboard";
    }

    @GetMapping("/quiz/{quizId}")
    public String takeQuiz(@PathVariable Long quizId, Model model) {
        try {
            System.out.println("Attempting to load quiz with ID: " + quizId);
            
            Quiz quiz = quizRepository.findById(quizId).orElse(null);
            if (quiz == null) {
                System.out.println("Quiz with ID " + quizId + " not found in database");
                return "redirect:/user/dashboard?error=Quiz not found. Please check if the quiz exists.";
            }
            
            System.out.println("Found quiz: " + quiz.getTitle() + " (Started: " + quiz.isStarted() + ")");
            
            if (!quiz.isStarted()) {
                System.out.println("Quiz " + quizId + " is not started yet");
                return "redirect:/user/dashboard?error=This quiz is not available yet. Please wait for the admin to start it.";
            }
            
            List<Question> questions = new ArrayList<>(questionRepository.findByQuiz(quiz));
            System.out.println("Found " + questions.size() + " questions for quiz " + quizId);
            
            if (questions.isEmpty()) {
                System.out.println("No questions found for quiz " + quizId);
                return "redirect:/user/dashboard?error=No questions available for this quiz. Please contact the administrator.";
            }
            
            model.addAttribute("quiz", quiz);
            model.addAttribute("questions", questions);
            System.out.println("Successfully loaded quiz and questions for quiz ID: " + quizId);
            return "user_take_quiz";
        } catch (Exception e) {
            System.err.println("Error loading quiz " + quizId + ": " + e.getMessage());
            e.printStackTrace();
            return "redirect:/user/dashboard?error=Error loading quiz: " + e.getMessage();
        }
    }

    @PostMapping("/quiz/submit")
    public String submitQuiz(@RequestParam Long quizId, @RequestParam Map<String, String> answers, Model model, HttpServletRequest request) {
        try {
            // Get current user with debugging
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            System.out.println("Authentication: " + authentication);
            System.out.println("Authentication name: " + (authentication != null ? authentication.getName() : "null"));
            
            String username = authentication != null ? authentication.getName() : null;
            
            // Find user with fallback mechanism
            User user = findCurrentUser(username, request);
            
            if (user == null) {
                return "redirect:/user/dashboard?error=User not found. Please log in again.";
            }
            
            Quiz quiz = quizRepository.findById(quizId).orElse(null);
            if (quiz == null) {
                return "redirect:/user/dashboard?error=Quiz not found";
            }
            
            // Check if user has already taken this quiz
            UserQuizResult existingResult = userQuizResultRepository.findByUserAndQuiz(user, quiz);
            if (existingResult != null) {
                return "redirect:/user/dashboard?error=You have already taken this quiz";
            }
            
            // Calculate score based on answers
            List<Question> questions = new ArrayList<>(questionRepository.findByQuiz(quiz));
            int correctAnswers = 0;
            int totalQuestions = questions.size();
            
            for (Question question : questions) {
                String answerKey = "answer_" + question.getQuesId();
                String userAnswer = answers.get(answerKey);
                
                if (userAnswer != null && userAnswer.equals(question.getAnswer())) {
                    correctAnswers++;
                }
            }
            
            // Calculate percentage and marks
            double percentage = totalQuestions > 0 ? (double) correctAnswers / totalQuestions * 100 : 0;
            int maxMarks = Integer.parseInt(quiz.getMaxMarks());
            int marksObtained = (int) (percentage * maxMarks / 100);
            
            // Convert answers to JSON string for storage
            String answersJson = answers.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(Collectors.joining(",", "{", "}"));
            
            // Save result to database
            UserQuizResult result = new UserQuizResult(user, quiz, marksObtained, maxMarks, percentage, answersJson);
            userQuizResultRepository.save(result);
            
            return "redirect:/user/dashboard/" + user.getUsername() + "?success=Quiz submitted successfully! Score: " + marksObtained + "/" + maxMarks + " (" + String.format("%.1f", percentage) + "%)";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/user/dashboard?error=Error submitting quiz: " + e.getMessage();
        }
    }

    @GetMapping("/studentresult")
    public String showStudentResults(Model model, HttpSession session, HttpServletRequest request) {
        // Get current user with proper authentication check
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Check if user is authenticated and not anonymous
        if (authentication == null || authentication.getName() == null || 
            "anonymousUser".equals(authentication.getName())) {
            return "redirect:/studentlogin?error=Please login to view your results";
        }
        
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);
        
        if (user == null) {
            return "redirect:/studentlogin?error=User not found. Please login again.";
        }
        
        System.out.println("=== Student Results Debug ===");
        System.out.println("Authenticated User: " + user.getUsername() + " (ID: " + user.getId() + ")");
        
        // Get all quiz results for the authenticated user only
        List<UserQuizResult> userResults = userQuizResultRepository.findByUser(user);
        System.out.println("Found " + userResults.size() + " quiz results for user");
        
        List<QuizResultDTO> results = new ArrayList<>();
        
        for (UserQuizResult result : userResults) {
            System.out.println("Processing result ID: " + result.getId() + 
                              ", Quiz: " + result.getQuiz().getTitle() + 
                              ", Marks: " + result.getMarksObtained() + "/" + result.getMaxMarks());
            
            QuizResultDTO dto = new QuizResultDTO();
            dto.setResultId(result.getId());
            dto.setExamTitle(result.getQuiz().getTitle());
            dto.setExamDescription(result.getQuiz().getDescription());
            dto.setMarksObtained(result.getMarksObtained());
            dto.setMaxMarks(Integer.parseInt(result.getQuiz().getMaxMarks()));
            dto.setPercentage(result.getPercentage());
            results.add(dto);
        }
        
        System.out.println("Total results DTOs created: " + results.size());
        System.out.println("=== End Debug ===");
        
        model.addAttribute("user", user);
        model.addAttribute("results", results);
        
        return "user_studentresult";
    }

    @GetMapping("/certificate/{resultId}")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long resultId, 
                                                     @RequestParam(value = "username", required = false) String username,
                                                     HttpServletRequest request) {
        try {
            System.out.println("=== Certificate Download Request ===");
            System.out.println("Result ID: " + resultId);
            System.out.println("Username: " + username);
            
            // Check authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null || 
                "anonymousUser".equals(authentication.getName())) {
                System.err.println("Unauthorized access attempt to certificate download");
                return ResponseEntity.status(401).build();
            }
            
            String authenticatedUsername = authentication.getName();
            System.out.println("Authenticated user: " + authenticatedUsername);
            
            // Find the quiz result first
            UserQuizResult result = userQuizResultRepository.findById(resultId).orElse(null);
            if (result == null) {
                System.err.println("Result not found for ID: " + resultId);
                return ResponseEntity.notFound().build();
            }
            
            System.out.println("Found result: Quiz=" + result.getQuiz().getTitle() + 
                              ", Marks=" + result.getMarksObtained() + "/" + result.getMaxMarks());
            
            // Get user from the result
            User user = result.getUser();
            System.out.println("User from result: " + user.getUsername() + " (ID: " + user.getId() + ")");
            
            // Verify that the authenticated user owns this result
            if (!user.getUsername().equals(authenticatedUsername)) {
                System.err.println("Access denied: User " + authenticatedUsername + " trying to access certificate for user " + user.getUsername());
                return ResponseEntity.status(403).build();
            }
            
            // If username parameter provided, verify it matches the authenticated user
            if (username != null && !username.trim().isEmpty()) {
                if (!username.trim().equals(authenticatedUsername)) {
                    System.err.println("Username parameter mismatch: " + username + " vs authenticated: " + authenticatedUsername);
                    return ResponseEntity.badRequest().build();
                }
            }
            
            // Create DTO for certificate generation with full user details
            QuizResultDTO resultDTO = new QuizResultDTO(
                result.getId(),
                result.getQuiz().getTitle(),
                result.getQuiz().getDescription(),
                result.getMarksObtained(),
                result.getMaxMarks(),
                user.getFirstName() + " " + user.getLastName(), // Use full name instead of username
                result.getExamDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            );
            
            System.out.println("=== Certificate Generation Debug ===");
            System.out.println("User ID: " + user.getId());
            System.out.println("Username: " + user.getUsername());
            System.out.println("First Name: " + user.getFirstName());
            System.out.println("Last Name: " + user.getLastName());
            System.out.println("Full Name for Certificate: " + (user.getFirstName() + " " + user.getLastName()));
            System.out.println("=== End Certificate Debug ===");
            
            byte[] pdfBytes = generateCertificatePDF(resultDTO);
            System.out.println("PDF generated successfully, size: " + pdfBytes.length + " bytes");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "certificate_" + user.getUsername() + "_" + resultId + ".pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            System.err.println("Error generating certificate for result ID " + resultId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    private byte[] generateCertificatePDF(QuizResultDTO result) throws DocumentException, IOException {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Add title
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, BaseColor.DARK_GRAY);
        Paragraph title = new Paragraph("Certificate of Completion", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30);
        document.add(title);
        
        // Add decorative line
        LineSeparator line = new LineSeparator();
        line.setLineWidth(2);
        document.add(new Chunk(line));
        document.add(new Paragraph(" "));
        
        // Add certificate content
        Font contentFont = FontFactory.getFont(FontFactory.HELVETICA, 14, BaseColor.BLACK);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);
        
        Paragraph content = new Paragraph();
        content.add(new Chunk("This is to certify that ", contentFont));
        content.add(new Chunk(result.getStudentName(), boldFont));
        content.add(new Chunk(" has successfully completed the examination ", contentFont));
        content.add(new Chunk(result.getExamTitle(), boldFont));
        content.add(new Chunk(" with outstanding performance.", contentFont));
        content.setAlignment(Element.ALIGN_CENTER);
        content.setSpacingAfter(20);
        document.add(content);
        
        // Add exam details
        Paragraph details = new Paragraph();
        details.add(new Chunk("Examination Details:", boldFont));
        details.setSpacingAfter(10);
        document.add(details);
        
        Paragraph examInfo = new Paragraph();
        examInfo.add(new Chunk("• Exam Title: " + result.getExamTitle(), contentFont));
        examInfo.add(new Chunk("\n• Description: " + result.getExamDescription(), contentFont));
        examInfo.add(new Chunk("\n• Marks Obtained: " + result.getMarksObtained() + "/" + result.getMaxMarks(), contentFont));
        examInfo.add(new Chunk("\n• Percentage: " + String.format("%.1f", result.getPercentage()) + "%", contentFont));
        examInfo.add(new Chunk("\n• Date: " + result.getExamDate(), contentFont));
        examInfo.setSpacingAfter(30);
        document.add(examInfo);
        
        // Add signature section
        Paragraph signature = new Paragraph();
        signature.add(new Chunk("_____________________", contentFont));
        signature.add(new Chunk("\nAuthorized Signature", contentFont));
        signature.setAlignment(Element.ALIGN_CENTER);
        document.add(signature);
        
        document.close();
        return baos.toByteArray();
    }

    @GetMapping("/quiz/result")
    public String quizResult(Model model) {
        // Populate with actual result data
        return "user_quiz_result";
    }

    @GetMapping("/dashboard/{username}")
    public String userSpecificDashboard(@PathVariable String username, Model model, HttpServletRequest request) {
        // Check authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || 
            "anonymousUser".equals(authentication.getName())) {
            return "redirect:/studentlogin?error=Please login to access dashboard";
        }
        
        String authenticatedUsername = authentication.getName();
        
        // Verify that the authenticated user is accessing their own dashboard
        if (!authenticatedUsername.equals(username)) {
            return "redirect:/studentlogin?error=You can only access your own dashboard";
        }
        
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return "redirect:/studentlogin?error=User not found. Please login again.";
        }
        
        System.out.println("=== User Specific Dashboard Debug ===");
        System.out.println("Authenticated User: " + user.getUsername() + " (ID: " + user.getId() + ")");
        System.out.println("Requested Dashboard: " + username);
        
        // Get all started quizzes
        List<Quiz> allQuizzes = quizRepository.findAll().stream()
            .filter(Quiz::isStarted)
            .collect(Collectors.toList());
        System.out.println("Total started quizzes: " + allQuizzes.size());
        
        // Get quizzes this user has already attempted
        List<UserQuizResult> userResults = userQuizResultRepository.findByUser(user);
        Set<Long> attemptedQuizIds = userResults.stream()
            .map(result -> result.getQuiz().getqId())
            .collect(Collectors.toSet());
        
        System.out.println("User has attempted " + attemptedQuizIds.size() + " quizzes");
        
        // Filter out quizzes the user has already attempted
        List<Quiz> availableQuizzes = allQuizzes.stream()
            .filter(quiz -> !attemptedQuizIds.contains(quiz.getqId()))
            .collect(Collectors.toList());
        
        System.out.println("Available quizzes for user: " + availableQuizzes.size());
        System.out.println("=== End Dashboard Debug ===");
        
        model.addAttribute("user", user);
        model.addAttribute("quizzes", availableQuizzes);
        
        // Add success/error messages from query parameters
        String success = request.getParameter("success");
        String error = request.getParameter("error");
        if (success != null) {
            model.addAttribute("successMessage", success);
        }
        if (error != null) {
            model.addAttribute("errorMessage", error);
        }
        
        return "user_dashboard";
    }

    @GetMapping("/studentresult/{username}")
    public String showUserSpecificStudentResults(@PathVariable String username, Model model, HttpSession session, HttpServletRequest request) {
        // Check authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || 
            "anonymousUser".equals(authentication.getName())) {
            return "redirect:/studentlogin?error=Please login to view your results";
        }
        
        String authenticatedUsername = authentication.getName();
        
        // Verify that the authenticated user is accessing their own results
        if (!authenticatedUsername.equals(username)) {
            return "redirect:/studentlogin?error=You can only access your own results";
        }
        
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return "redirect:/studentlogin?error=User not found. Please login again.";
        }
        
        System.out.println("=== User Specific Student Results Debug ===");
        System.out.println("Authenticated User: " + user.getUsername() + " (ID: " + user.getId() + ")");
        System.out.println("Requested Results: " + username);
        
        // Get all quiz results for the authenticated user only
        List<UserQuizResult> userResults = userQuizResultRepository.findByUser(user);
        System.out.println("Found " + userResults.size() + " quiz results for user");
        
        List<QuizResultDTO> results = new ArrayList<>();
        
        for (UserQuizResult result : userResults) {
            System.out.println("Processing result ID: " + result.getId() + 
                              ", Quiz: " + result.getQuiz().getTitle() + 
                              ", Marks: " + result.getMarksObtained() + "/" + result.getMaxMarks());
            
            QuizResultDTO dto = new QuizResultDTO();
            dto.setResultId(result.getId());
            dto.setExamTitle(result.getQuiz().getTitle());
            dto.setExamDescription(result.getQuiz().getDescription());
            dto.setMarksObtained(result.getMarksObtained());
            dto.setMaxMarks(Integer.parseInt(result.getQuiz().getMaxMarks()));
            dto.setPercentage(result.getPercentage());
            results.add(dto);
        }
        
        System.out.println("Total results DTOs created: " + results.size());
        System.out.println("=== End Debug ===");
        
        model.addAttribute("user", user);
        model.addAttribute("results", results);
        
        return "user_studentresult";
    }

    // Helper method to find current user with fallback
    private User findCurrentUser(String username, HttpServletRequest request) {
        User user = null;
        
        // First try to get user from session
        user = (User) request.getSession().getAttribute("currentUser");
        if (user != null) {
            System.out.println("Found user in session: " + user.getUsername());
            return user;
        }
        
        // Try to find user by username if provided
        if (username != null && !username.equals("anonymousUser")) {
            user = userRepository.findByUsername(username);
            System.out.println("Found user by username: " + (user != null ? user.getUsername() : "null"));
        }
        
        // If user not found, try to get ANY available user (for testing purposes)
        if (user == null) {
            List<User> allUsers = userRepository.findAll();
            System.out.println("Total users in database: " + allUsers.size());
            
            if (!allUsers.isEmpty()) {
                // Just get the first user for now, regardless of role
                user = allUsers.get(0);
                System.out.println("Using first available user: " + user.getUsername());
            } else {
                // Create a test user if no users exist
                System.out.println("No users found in database! Creating a test user...");
                user = createTestUser();
            }
            
            // Store in session for future use
            if (user != null) {
                request.getSession().setAttribute("currentUser", user);
                System.out.println("Stored user in session: " + user.getUsername());
            }
            
            // Print all users for debugging
            List<User> allUsersAfter = userRepository.findAll();
            allUsersAfter.forEach(u -> {
                System.out.println("Available user: " + u.getUsername() + " (ID: " + u.getId() + ")");
                u.getUserRoles().forEach(ur -> {
                    System.out.println("  - Role: " + ur.getRole().getRoleName());
                });
            });
        }
        
        return user;
    }
    
    // Create a test user if none exists
    private User createTestUser() {
        try {
            // Create test user
            User testUser = new User();
            testUser.setUsername("testuser");
            testUser.setPassword("$2a$10$1qAz2wSx3eCc6gDDddIWeOe8j8gGqK9vL8mN0oP1qRs2tUvWxYz3"); // "password"
            testUser.setFirstName("Test");
            testUser.setLastName("User");
            testUser.setEmail("test@example.com");
            testUser.setPhone("1234567890");
            testUser.setEnabled(true);
            testUser.setProfile("default.png");
            
            testUser = userRepository.save(testUser);
            System.out.println("Created test user: " + testUser.getUsername());
            
            return testUser;
        } catch (Exception e) {
            System.err.println("Error creating test user: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
} 