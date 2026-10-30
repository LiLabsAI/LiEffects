cmake_minimum_required(VERSION 3.22.1)
project(photoeffects CXX)

add_library(photoeffects SHARED photoeffects.cpp)

target_compile_features(photoeffects PRIVATE cxx_std_17)
target_compile_options(photoeffects PRIVATE
    -O3
    -fno-exceptions
    -fno-rtti
    -fvisibility=hidden
    # Keep IEEE arithmetic ordering aligned with the Kotlin reference implementation.
    -ffp-contract=off
)
