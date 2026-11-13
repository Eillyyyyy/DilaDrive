package com.diladrive.repository;

import com.diladrive.entity.VerificationPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VerificationPhotoRepository extends JpaRepository<VerificationPhoto, Long> {
    List<VerificationPhoto> findByAssignmentAssignmentIdOrderByUploadedAtDesc(Long assignmentId);
    Optional<VerificationPhoto> findByPhotoIdAndAssignmentAssignmentId(Long photoId, Long assignmentId);
}
