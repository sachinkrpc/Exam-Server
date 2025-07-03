package com.exam.model;

public class QuizResultDTO {
    private Long resultId;
    private String examTitle;
    private String examDescription;
    private Integer marksObtained;
    private Integer maxMarks;
    private Double percentage;
    private String studentName;
    private String examDate;

    public QuizResultDTO() {
    }

    public QuizResultDTO(Long resultId, String examTitle, String examDescription, 
                        Integer marksObtained, Integer maxMarks, String studentName, String examDate) {
        this.resultId = resultId;
        this.examTitle = examTitle;
        this.examDescription = examDescription;
        this.marksObtained = marksObtained;
        this.maxMarks = maxMarks;
        this.studentName = studentName;
        this.examDate = examDate;
        this.percentage = maxMarks > 0 ? (double) marksObtained / maxMarks * 100 : 0.0;
    }

    // Getters and Setters
    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public void setExamTitle(String examTitle) {
        this.examTitle = examTitle;
    }

    public String getExamDescription() {
        return examDescription;
    }

    public void setExamDescription(String examDescription) {
        this.examDescription = examDescription;
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

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getExamDate() {
        return examDate;
    }

    public void setExamDate(String examDate) {
        this.examDate = examDate;
    }
} 