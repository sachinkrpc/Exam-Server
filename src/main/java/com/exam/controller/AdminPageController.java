package com.exam.controller;

import com.exam.model.exam.Question;
import com.exam.model.exam.Quiz;
import com.exam.model.User;
import com.exam.repo.QuestionRepository;
import com.exam.repo.QuizRepository;
import com.exam.repo.RoleRepository;
import com.exam.repo.UserRepository;
import com.exam.service.QuestionService;
import com.exam.service.QuizService;
import com.exam.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import com.exam.model.UserQuizResult;
import com.exam.repo.UserQuizResultRepository;

@Controller
@RequestMapping("/admin")
public class AdminPageController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserQuizResultRepository userQuizResultRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        return "admin_dashboard";
    }

    @GetMapping("/users")
    public String users(Model model) {
        return "admin_users";
    }

    @GetMapping("/quizzes")
    public String quizzes(Model model) {
        return "admin_quizzes";
    }

    @GetMapping("/exammanage")
    public String examManage(Model model) {
        List<Quiz> exams = quizRepository.findAll();
        model.addAttribute("exams", exams);
        return "admin_exammanage";
    }

    @GetMapping("/exammanage/start/{examId}")
    public String startExam(@PathVariable Long examId, RedirectAttributes redirectAttributes) {
        try {
            Quiz quiz = quizRepository.findById(examId).orElse(null);
            if (quiz != null) {
                quiz.setStarted(true);
                quizRepository.save(quiz);
                redirectAttributes.addFlashAttribute("successMessage", "Exam '" + quiz.getTitle() + "' has been started successfully!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Exam not found!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error starting exam: " + e.getMessage());
        }
        return "redirect:/admin/exammanage";
    }

    @GetMapping("/exammanage/delete/{examId}")
    public String deleteExam(@PathVariable Long examId, RedirectAttributes redirectAttributes) {
        try {
            Quiz quiz = quizRepository.findById(examId).orElse(null);
            if (quiz != null) {
                // First, delete all quiz results associated with this quiz
                List<UserQuizResult> quizResults = userQuizResultRepository.findByQuiz(quiz);
                if (!quizResults.isEmpty()) {
                    userQuizResultRepository.deleteAll(quizResults);
                    System.out.println("Deleted " + quizResults.size() + " quiz results for quiz: " + quiz.getTitle());
                }
                
                // Then delete the quiz
                quizRepository.delete(quiz);
                redirectAttributes.addFlashAttribute("successMessage", "Exam '" + quiz.getTitle() + "' has been deleted successfully!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Exam not found!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting exam: " + e.getMessage());
            System.err.println("Error deleting exam: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/admin/exammanage";
    }

    @PostMapping("/quizzes/add")
    public String addQuiz(@RequestParam String title,
                         @RequestParam String description,
                         @RequestParam String maxMarks,
                         @RequestParam String numberOfQuestions,
                         @RequestParam(defaultValue = "0") Integer durationHours,
                         @RequestParam(defaultValue = "0") Integer durationMinutes,
                         Model model,
                         HttpServletRequest request) {
        try {
            Quiz quiz = new Quiz();
            quiz.setTitle(title);
            quiz.setDescription(description);
            quiz.setMaxMarks(maxMarks);
            quiz.setNumberOfQuestions(numberOfQuestions);
            quiz.setActive(false);
            quiz.setStarted(false);
            
            // Calculate total duration in minutes
            int totalDurationMinutes = (durationHours * 60) + durationMinutes;
            quiz.setDurationMinutes(totalDurationMinutes);
            
            Quiz savedQuiz = quizService.addQuiz(quiz);
            
            // Store the quiz ID in session for questions
            request.getSession().setAttribute("currentQuizId", savedQuiz.getqId());
            
            // Redirect to questions page with success message and total questions count
            return "redirect:/admin/questions?success=Quiz added successfully! You can now add questions.&total=" + numberOfQuestions + "&quizId=" + savedQuiz.getqId();
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error adding quiz: " + e.getMessage());
            return "admin_quizzes";
        }
    }
    
    @GetMapping("/questions")
    public String questionsPage(@RequestParam(value = "total", defaultValue = "1") String totalQuestions,
                               @RequestParam(value = "current", defaultValue = "1") String currentQuestion,
                               @RequestParam(value = "quizId", required = false) Long quizId,
                               HttpServletRequest request,
                               Model model) {
        // Get quiz ID from session if not in URL
        if (quizId == null) {
            quizId = (Long) request.getSession().getAttribute("currentQuizId");
        }
        
        if (quizId == null) {
            return "redirect:/admin/quizzes?error=No quiz selected for adding questions";
        }
        
        // Get the quiz to display its title
        Quiz quiz = quizService.getQuiz(quizId);
        if (quiz == null) {
            return "redirect:/admin/quizzes?error=Quiz not found";
        }
        
        model.addAttribute("totalQuestions", totalQuestions);
        model.addAttribute("currentQuestion", currentQuestion);
        model.addAttribute("quizId", quizId);
        model.addAttribute("quizTitle", quiz.getTitle());
        return "admin_questions";
    }
    
    @PostMapping("/questions/add")
    public String addQuestion(@RequestParam String content,
                             @RequestParam String answer,
                             @RequestParam(defaultValue = "1") String questionNumber,
                             @RequestParam Long quizId,
                             HttpServletRequest request,
                             Model model) {
        try {
            // Get the quiz
            Quiz quiz = quizService.getQuiz(quizId);
            if (quiz == null) {
                return "redirect:/admin/quizzes?error=Quiz not found";
            }
            
            // Get all option parameters from the request
            Map<String, String[]> parameterMap = request.getParameterMap();
            List<String> options = new ArrayList<>();
            
            // Extract options (option1, option2, option3, etc.)
            for (int i = 1; i <= 10; i++) { // Support up to 10 options
                String optionKey = "option" + i;
                if (parameterMap.containsKey(optionKey)) {
                    String optionValue = request.getParameter(optionKey);
                    if (optionValue != null && !optionValue.trim().isEmpty()) {
                        options.add(optionValue.trim());
                    }
                }
            }
            
            if (options.size() < 2) {
                return "redirect:/admin/questions?error=At least 2 options are required&total=" + request.getParameter("total") + "&quizId=" + quizId;
            }
            
            // Validate correct answer
            int correctAnswerIndex;
            try {
                correctAnswerIndex = Integer.parseInt(answer) - 1; // Convert to 0-based index
                if (correctAnswerIndex < 0 || correctAnswerIndex >= options.size()) {
                    return "redirect:/admin/questions?error=Invalid correct answer selection&total=" + request.getParameter("total") + "&quizId=" + quizId;
                }
            } catch (NumberFormatException e) {
                return "redirect:/admin/questions?error=Invalid correct answer format&total=" + request.getParameter("total") + "&quizId=" + quizId;
            }
            
            // Create question object
            Question question = new Question();
            question.setContent(content);
            question.setAnswer(options.get(correctAnswerIndex)); // Store the actual answer text
            question.setQuiz(quiz); // Associate with the quiz
            
            // For now, we'll store options as a JSON string or in a separate field
            // You might want to create a separate QuestionOption entity for better structure
            question.setOption1(options.size() > 0 ? options.get(0) : "");
            question.setOption2(options.size() > 1 ? options.get(1) : "");
            question.setOption3(options.size() > 2 ? options.get(2) : "");
            question.setOption4(options.size() > 3 ? options.get(3) : "");
            
            // Save the question
            questionService.addQuestion(question);
            
            // Get total questions from URL parameter
            String totalQuestions = request.getParameter("total");
            int currentQuestionNum = Integer.parseInt(questionNumber);
            int totalQuestionsNum = totalQuestions != null ? Integer.parseInt(totalQuestions) : 1;
            
            // Check if this is the last question
            if (currentQuestionNum >= totalQuestionsNum) {
                // Clear the session
                request.getSession().removeAttribute("currentQuizId");
                return "redirect:/admin/quizzes?success=All questions added successfully! Quiz '" + quiz.getTitle() + "' is ready.";
            } else {
                // Move to next question
                int nextQuestion = currentQuestionNum + 1;
                return "redirect:/admin/questions?success=Question " + currentQuestionNum + " added successfully!&total=" + totalQuestions + "&current=" + nextQuestion + "&quizId=" + quizId;
            }
        } catch (Exception e) {
            return "redirect:/admin/questions?error=Error adding question: " + e.getMessage() + "&total=" + request.getParameter("total") + "&quizId=" + quizId;
        }
    }

    @GetMapping("/questions/complete")
    public String completeQuestions(HttpServletRequest request) {
        // Clear the session
        request.getSession().removeAttribute("currentQuizId");
        return "redirect:/admin/quizzes?success=Question adding completed!";
    }

    @GetMapping("/studentdetail")
    public String studentDetail(Model model) {
        List<User> allUsers = userRepository.findAll();
        // Filter users with NORMAL role
        List<User> students = allUsers.stream()
            .filter(user -> user.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equalsIgnoreCase("NORMAL")))
            .toList();
        
        // Get all quizzes
        List<Quiz> allQuizzes = quizRepository.findAll();
        
        // Get exam results for each student
        Map<Long, List<UserQuizResult>> userResults = new HashMap<>();
        
        for (User student : students) {
            List<UserQuizResult> results = userQuizResultRepository.findByUser(student);
            userResults.put(student.getId(), results);
        }
        
        model.addAttribute("students", students);
        model.addAttribute("allQuizzes", allQuizzes);
        model.addAttribute("userResults", userResults);
        
        return "admin_studentdetail";
    }

    @GetMapping("/studentdetail/delete/{userId}")
    public String deleteStudent(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                // First delete all exam results for this user
                List<UserQuizResult> userResults = userQuizResultRepository.findByUser(user);
                if (!userResults.isEmpty()) {
                    userQuizResultRepository.deleteAll(userResults);
                    System.out.println("Deleted " + userResults.size() + " exam results for user: " + user.getUsername());
                }
                
                // Now delete the user
                userRepository.delete(user);
                redirectAttributes.addFlashAttribute("successMessage", "Student '" + user.getUsername() + "' and all their exam results have been deleted successfully!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Student not found!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting student: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/admin/studentdetail";
    }
} 