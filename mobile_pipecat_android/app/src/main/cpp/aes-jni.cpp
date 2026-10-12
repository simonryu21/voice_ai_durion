/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <jni.h>

#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <android/log.h>

#include <stdint.h>
#include "aes.h"

uint8_t key[] = { 0x60, 0x3d, 0xeb, 0x10, 0x15, 0xca, 0x71, 0xbe, 0x2b, 0x73, 0xae, 0xf0, 0x85, 0x7d, 0x77, 0x81,
                  0x1f, 0x35, 0x2c, 0x07, 0x3b, 0x61, 0x08, 0xd7, 0x2d, 0x98, 0x10, 0xa3, 0x09, 0x14, 0xdf, 0xf4 };

const uint8_t iv[]  = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f };

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lgcns_codeengine_util_AESEncryptor_encrypt(JNIEnv *env, jobject object, jbyteArray data,
                                                  jint length) {
    jint orgLen = length;
    while (length % AES_BLOCKLEN != 0) length++;

    unsigned char* data_array = new unsigned char[length];
    memset(data_array, 0, length);
    env->GetByteArrayRegion (data, 0, orgLen, reinterpret_cast<jbyte*>(data_array));

    struct AES_ctx ctx;
    AES_init_ctx_iv(&ctx, key, iv);
    AES_CBC_encrypt_buffer(&ctx, data_array, (uint32_t)length);

    jbyteArray resArray = env->NewByteArray((jsize)length);
    env->SetByteArrayRegion(resArray, 0, length, reinterpret_cast<const jbyte*>(data_array));

    return resArray;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lgcns_codeengine_util_AESEncryptor_decrypt(JNIEnv *env, jobject object, jbyteArray data,
                                                  jint length) {
    jint orgLen = length;
    while (length % AES_BLOCKLEN != 0) length++;

    unsigned char* data_array = new unsigned char[length];
    memset(data_array, 0, length);

    env->GetByteArrayRegion (data, 0, orgLen, reinterpret_cast<jbyte*>(data_array));

    struct AES_ctx ctx;
    AES_init_ctx_iv(&ctx, key, iv);
    AES_CBC_decrypt_buffer(&ctx, data_array, (uint32_t)length);

    jbyteArray resArray = env->NewByteArray((jsize)length);
    env->SetByteArrayRegion(resArray, 0, length, reinterpret_cast<const jbyte*>(data_array));

    return resArray;
}
