package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.dto.ProgramResponse;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramMapper;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.service.ProgramService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private final ProgramRepository programRepository;
    private final ProgramMapper programMapper;

    @Override
    public PageResponse<ProgramResponse> getPrograms(int page, int size) {
        PageableValidationUtils.validate(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        Page<Program> programPage = programRepository.findAll(pageable);
        List<ProgramResponse> content = programMapper.toResponseList(programPage.getContent());
        return PageResponse.of(content, programPage.getNumber(), programPage.getSize(), programPage.getTotalElements());
    }

    @Override
    public ProgramResponse getProgramById(String id) {
        ObjectIdUtils.requireValid(id, "id");
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
        return programMapper.toResponse(program);
    }

    @Override
    public ProgramResponse createProgram(ProgramRequest request) {
        String code = request.getCode().trim();
        if (programRepository.existsByCode(code)) {
            throw conflict(code);
        }

        Program program = Program.builder()
                .code(code)
                .name(request.getName().trim())
                .level(trimToNull(request.getLevel()))
                .degree(trimToNull(request.getDegree()))
                .build();

        try {
            program = programRepository.save(program);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate program code on create: {}", code);
            throw conflict(code);
        }
        return programMapper.toResponse(program);
    }

    @Override
    public ProgramResponse updateProgram(String id, ProgramRequest request) {
        ObjectIdUtils.requireValid(id, "id");
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));

        String code = request.getCode().trim();
        if (!code.equals(program.getCode()) && programRepository.existsByCodeAndIdNot(code, id)) {
            throw conflict(code);
        }

        program.setCode(code);
        program.setName(request.getName().trim());
        program.setLevel(trimToNull(request.getLevel()));
        program.setDegree(trimToNull(request.getDegree()));

        try {
            program = programRepository.save(program);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate program code on update: {}", code);
            throw conflict(code);
        }
        return programMapper.toResponse(program);
    }

    private AppException conflict(String code) {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                "Mã chương trình đào tạo đã tồn tại: " + code);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
