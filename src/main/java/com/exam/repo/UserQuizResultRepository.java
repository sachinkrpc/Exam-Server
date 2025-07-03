package com.exam.repo;

import com.exam.model.UserQuizResult;
import com.exam.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserQuizResultRepository extends JpaRepository<UserQuizResult, Long> {
    
    List<UserQuizResult> findByUser(User user);
    
    List<UserQuizResult> findByUserOrderByExamDateDesc(User user);
    
    UserQuizResult findByUserAndQuiz(User user, com.exam.model.exam.Quiz quiz);
    
    List<UserQuizResult> findByQuiz(com.exam.model.exam.Quiz quiz);
} 