package com.secufusion.iam.controller;

import com.secufusion.iam.dto.RegionDTO;
import com.secufusion.iam.service.RegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/regions")
public class RegionController {

    @Autowired
    private RegionService regionService;

    @GetMapping
    public List<RegionDTO> getRegions() {
        return regionService.getAllRegions();
    }
}
