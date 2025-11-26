package com.secufusion.iam.service;

import com.secufusion.iam.dto.RegionDTO;
import com.secufusion.iam.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;

    public List<RegionDTO> getAllRegions() {
        return regionRepository.findAll()
                .stream()
                .map(region ->
                        new RegionDTO(region.getPkRegionId(), region.getRegionName())
                )
                .toList();
    }
}
