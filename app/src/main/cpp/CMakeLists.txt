cmake_minimum_required(VERSION 3.22.1)
project(vcode_pty C)

add_library(vcode-pty SHARED
    vcode-pty.c
)

find_library(log-lib log)

target_compile_options(vcode-pty PRIVATE
    -O3
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -Wall
    -Wextra
    -Werror
)

target_link_options(vcode-pty PRIVATE
    -Wl,--gc-sections
    -Wl,-z,relro
    -Wl,-z,now
)

target_link_libraries(vcode-pty
    ${log-lib}
)
