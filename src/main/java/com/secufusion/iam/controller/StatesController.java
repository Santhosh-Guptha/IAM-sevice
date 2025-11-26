package com.secufusion.iam.controller;

import com.secufusion.iam.entity.Cities;
import com.secufusion.iam.entity.States;
import com.secufusion.iam.repository.CityRepository;
import com.secufusion.iam.service.StatesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/states")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class StatesController {

    @Autowired
    private StatesService statesService;

    @Autowired
    private CityRepository cityRepository;


    @GetMapping("/id")
    public ResponseEntity<States> getState(@RequestParam Long id) {
        return ResponseEntity.ok(statesService.getState(id));
    }

    @GetMapping
    public ResponseEntity<List<States>> getAllStates() {
        return ResponseEntity.ok(statesService.getAllStates());
    }

    @GetMapping("/country")
    public ResponseEntity<List<States>> getStatesByCountry(@RequestParam Long countryId) {
        return ResponseEntity.ok(statesService.getStatesByCountry(countryId));
    }

    @GetMapping("/cities")
    public ResponseEntity<List<Cities>> getCitiesByStateId(@RequestParam Long stateId){
        return ResponseEntity.ok(cityRepository.findAllByStateId(stateId));
    }

}

