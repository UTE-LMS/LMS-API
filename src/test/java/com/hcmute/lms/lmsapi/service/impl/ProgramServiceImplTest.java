package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.dto.ProgramResponse;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramMapper;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgramServiceImpl Unit Tests (F02 / US-05)")
class ProgramServiceImplTest {

    private static final String PROGRAM_ID = "671dcba5f123456789abc001";

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ProgramMapper programMapper;

    @InjectMocks
    private ProgramServiceImpl programService;

    private ProgramRequest request() {
        return ProgramRequest.builder()
                .code(" CNTT ")
                .name(" Công nghệ thông tin ")
                .level("Đại học")
                .degree("Kỹ sư")
                .build();
    }

    @Test
    @DisplayName("Tạo chương trình đào tạo thành công, chuẩn hóa khoảng trắng")
    void createProgram_success() {
        when(programRepository.existsByCode("CNTT")).thenReturn(false);
        when(programRepository.save(any(Program.class))).thenAnswer(invocation -> {
            Program program = invocation.getArgument(0);
            program.setId(PROGRAM_ID);
            return program;
        });
        when(programMapper.toResponse(any(Program.class))).thenReturn(
                ProgramResponse.builder().id(PROGRAM_ID).code("CNTT").build());

        ProgramResponse response = programService.createProgram(request());

        assertThat(response.getId()).isEqualTo(PROGRAM_ID);
        verify(programRepository).save(any(Program.class));
    }

    @Test
    @DisplayName("Tạo chương trình trùng mã trả 409 CONFLICT")
    void createProgram_duplicateCode_conflict() {
        when(programRepository.existsByCode("CNTT")).thenReturn(true);

        assertThatThrownBy(() -> programService.createProgram(request()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(programRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy chương trình không tồn tại trả 404")
    void getProgram_notFound() {
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programService.getProgramById(PROGRAM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Cập nhật đổi mã trùng chương trình khác trả 409")
    void updateProgram_duplicateCode_conflict() {
        Program existing = Program.builder().id(PROGRAM_ID).code("OLD").name("Cũ").build();
        when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(existing));
        when(programRepository.existsByCodeAndIdNot("CNTT", PROGRAM_ID)).thenReturn(true);

        assertThatThrownBy(() -> programService.updateProgram(PROGRAM_ID, request()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
