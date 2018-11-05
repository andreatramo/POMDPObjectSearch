cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_VERBOSE_MAKEFILE on)

include_directories(${PROJECT_SOURCE_DIR}/src/main/cpp/include/)

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

add_library(JNI
            SHARED
            src/main/cpp/src/jniWrapper.cpp
            )


# OpenCV lib linking
add_library(Yolo
        SHARED
        src/main/cpp/src/ObjectDetector/Helper.cpp
        )


# Link OpenAL and OpenCV into single JNI lib
target_link_libraries(JNI
        SoundGenerator
        )

