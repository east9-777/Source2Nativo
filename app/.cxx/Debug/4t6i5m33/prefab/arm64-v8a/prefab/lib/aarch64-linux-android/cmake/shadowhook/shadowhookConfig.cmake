if(NOT TARGET shadowhook::shadowhook)
add_library(shadowhook::shadowhook SHARED IMPORTED)
set_target_properties(shadowhook::shadowhook PROPERTIES
    IMPORTED_LOCATION "C:/Users/maria/.gradle/caches/8.13/transforms/8c7804e8a26806db0790c7b8ff33d7ff/transformed/shadowhook-1.0.9/prefab/modules/shadowhook/libs/android.arm64-v8a/libshadowhook.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/maria/.gradle/caches/8.13/transforms/8c7804e8a26806db0790c7b8ff33d7ff/transformed/shadowhook-1.0.9/prefab/modules/shadowhook/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

