package com.exam.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import com.exam.model.exam.Quiz;

@Entity
@Table(name = "user_quiz_results")
public class UserQuizResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;
    
    @ManyToOne(fetch = FetchType.EAGER)
    private Quiz quiz;
    
    private Integer marksObtained;
    private Integer maxMarks;
    private Double percentage;
    private LocalDateTime examDate;
    private String answers; // JSON string of user answers
    
    public UserQuizResult() {
    }
    
    public UserQuizResult(User user, Quiz quiz, Integer marksObtained, 
                         Integer maxMarks, Double percentage, String answers) {
        this.user = user;
        this.quiz = quiz;
        this.marksObtained = marksObtained;
        this.maxMarks = maxMarks;
        this.percentage = percentage;
        this.answers = answers;
        this.examDate = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public Quiz getQuiz() {
        return quiz;
    }
    
    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }
    
    public Integer getMarksObtained() {
        return marksObtained;
    }
    
    public void setMarksObtained(Integer marksObtained) {
        this.marksObtained = marksObtained;
    }
    
    public Integer getMaxMarks() {
        return maxMarks;
    }
    
    public void setMaxMarks(Integer maxMarks) {
        this.maxMarks = maxMarks;
    }
    
    public Double getPercentage() {
        return percentage;
    }
    
    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }
    
    public LocalDateTime getExamDate() {
        return examDate;
    }
    
    public void setExamDate(LocalDateTime examDate) {
        this.examDate = examDate;
    }
    
    public String getAnswers() {
        return answers;
    }
    
    public void setAnswers(String answers) {
        this.answers = answers;
    }
} 