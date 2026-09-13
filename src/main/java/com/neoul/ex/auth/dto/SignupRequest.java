package com.neoul.ex.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 10, message = "아이디는 10자 이하여야 합니다.")
        // 영문과 숫자를 각각 하나 이상 포함하고 영문과 숫자만 허용한다.
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]+$",
                message = "아이디는 영문과 숫자를 모두 포함해야 하며 영문과 숫자만 사용할 수 있습니다."
        )
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 16, message = "비밀번호는 8자 이상 16자 이하여야 합니다.")
        // 영문과 숫자를 각각 하나 이상 포함하고 영문과 숫자가 아닌 특수문자를 하나 이상 포함한다.
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,16}$",
                message = "비밀번호는 영문과 숫자와 특수문자를 모두 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "비밀번호 확인은 필수입니다.")
        String passwordConfirm,

        @NotNull(message = "해수욕장 선택은 필수입니다.")
        Long beachId
) {
}
