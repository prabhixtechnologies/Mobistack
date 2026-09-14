package com.fixflow.commons.repository;

import com.fixflow.commons.domain.CommonsReviewer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommonsReviewerRepository extends JpaRepository<CommonsReviewer, UUID> {

    List<CommonsReviewer> findAllByOrderByGrantedAtDesc();
}
