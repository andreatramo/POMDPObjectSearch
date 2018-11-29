cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_VERBOSE_MAKEFILE on)

include_directories(${PROJECT_SOURCE_DIR}/src/main/cpp/include/)

# set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11 -fexceptions -frtti") #for OpenCV
set(JNILIBS "${CMAKE_SOURCE_DIR}/src/main/jniLibs")

# OpenAL setup
add_library(openal SHARED IMPORTED )
set_target_properties(openal PROPERTIES
        IMPORTED_LOCATION ${JNILIBS}/${ANDROID_ABI}/libopenal.so
        )

# Sound lib linking
add_library(SoundGenerator
            SHARED
            src/main/cpp/src/SoundGenerator/SoundGenerator.cpp
            )

target_link_libraries(SoundGenerator
                      openal
                      log
                      )

# Link OpenAL and OpenCV into single JNI lib

add_library(JNI
        SHARED
        src/main/cpp/src/jniWrapper.cpp
        )

target_link_libraries(JNI
        SoundGenerator
        )

