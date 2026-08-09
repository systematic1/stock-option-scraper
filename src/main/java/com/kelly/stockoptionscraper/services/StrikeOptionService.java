package com.kelly.stockoptionscraper.services;

import com.kelly.stockoptionscraper.models.StrikeOptionData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrikeOptionService extends JpaRepository<StrikeOptionData, Integer> {
}
