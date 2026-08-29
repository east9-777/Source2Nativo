if(NOT TARGET shadowhook::shadowhook)
add_library(shadowhook::shadowhook SHARED IMPORTED)
set_target_properties(shadowhook::shadowhook PROPERTIES
    IMPORTED_LOCATION "C:/Users/thziim/.gradle/caches/8.9/transforms/f6aca9a487d7dd82d2b1f49867567a2c/transformed/shadowhook-1.0.9/prefab/modules/shadowhook/libs/android.armeabi-v7a/libshadowhook.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/thziim/.gradle/caches/8.9/transforms/f6aca9a487d7dd82d2b1f49867567a2c/transformed/shadowhook-1.0.9/prefab/modules/shadowhook/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

