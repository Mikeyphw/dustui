cmake_minimum_required(VERSION 3.22.1)
project(dustui_scanner LANGUAGES CXX)

add_library(dustui_scanner SHARED dustui_scanner.cpp)

target_compile_features(dustui_scanner PRIVATE cxx_std_17)
target_compile_options(dustui_scanner PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
set_target_properties(dustui_scanner PROPERTIES CXX_VISIBILITY_PRESET hidden)
