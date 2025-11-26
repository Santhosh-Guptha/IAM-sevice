package com.secufusion.iam.service;

import com.secufusion.iam.entity.States;
import com.secufusion.iam.repository.StatesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatesService {

    @Autowired
    private StatesRepository statesRepository;


    // GET BY ID
    public States getState(Long id) {
        return statesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("State not found"));
    }

    // GET ALL
    public List<States> getAllStates() {
        return statesRepository.findAll();
    }

    // GET BY COUNTRY
    public List<States> getStatesByCountry(Long countryId) {
        return statesRepository.findAllByCountryId(countryId);
    }

}

