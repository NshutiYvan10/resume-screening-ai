package com.resumeai.repository;

import com.resumeai.domain.CandidateDemographics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CandidateDemographicsRepository extends JpaRepository<CandidateDemographics, UUID> {
}
