package com.resumeai.repository;

import com.resumeai.domain.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByApplicationId(UUID applicationId);
}
