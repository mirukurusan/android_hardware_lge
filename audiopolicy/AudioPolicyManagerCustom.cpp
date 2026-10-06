/*
 * Copyright (C) 2026 The LineageOS Project
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
 */

#define LOG_TAG "APM_Custom_LGE"
#define LOG_NDEBUG 0

#include <log/log.h>
#include <AudioPolicyConfig.h>
#include <EngineLibrary.h>
#include "AudioPolicyManagerCustom.h"

namespace android {

AudioPolicyManagerCustom::AudioPolicyManagerCustom(
        const sp<const AudioPolicyConfig>& config,
        EngineInstance&& engine,
        AudioPolicyClientInterface *clientInterface)
    : AudioPolicyManager(config, std::move(engine), clientInterface) {
    ALOGI("%s: LG Quad DAC custom audio policy manager initialized", __func__);
}

status_t AudioPolicyManagerCustom::getOutputForAttr(
        const audio_attributes_t *attr,
        audio_io_handle_t *output,
        audio_session_t session,
        audio_stream_type_t *stream,
        const AttributionSourceState& attributionSource,
        audio_config_t *config,
        audio_output_flags_t *flags,
        DeviceIdVector *selectedDeviceIds,
        audio_port_handle_t *portId,
        std::vector<audio_io_handle_t> *secondaryOutputs,
        output_type_t *outputType,
        bool *isSpatialized,
        bool *isBitPerfect) {

    audio_stream_type_t streamType = mEngine->getStreamTypeForAttributes(*attr);

    if (streamType == AUDIO_STREAM_MUSIC &&
            audio_is_linear_pcm(config->format) &&
            (config->sample_rate > 48000 || audio_bytes_per_sample(config->format) > 2)) {
        *flags = (audio_output_flags_t)((*flags & ~AUDIO_OUTPUT_FLAG_DEEP_BUFFER) | AUDIO_OUTPUT_FLAG_DIRECT);
        ALOGI("%s: Requesting DIRECT for Hi-Res PCM (rate=%u, format=0x%x, flags=0x%x)",
              __func__, config->sample_rate, config->format, *flags);
    }

    return AudioPolicyManager::getOutputForAttr(
            attr, output, session, stream, attributionSource, config,
            flags, selectedDeviceIds, portId, secondaryOutputs,
            outputType, isSpatialized, isBitPerfect);
}

extern "C" AudioPolicyInterface* createAudioPolicyManager(
        AudioPolicyClientInterface *clientInterface) {
    AudioPolicyManagerCustom *apm = nullptr;
    media::AudioPolicyConfig apmConfig;
    if (status_t status = clientInterface->getAudioPolicyConfig(&apmConfig); status == OK) {
        auto config = AudioPolicyConfig::loadFromApmAidlConfigWithFallback(apmConfig);
        ALOGD("%s: loading APM engine %s", __func__, config->getEngineLibraryNameSuffix().c_str());
        apm = new AudioPolicyManagerCustom(
                config,
                loadApmEngineLibraryAndCreateEngine(
                        config->getEngineLibraryNameSuffix(), apmConfig.engineConfig),
                clientInterface);
    } else {
        auto config = AudioPolicyConfig::loadFromApmXmlConfigWithFallback();
        apm = new AudioPolicyManagerCustom(
                config,
                loadApmEngineLibraryAndCreateEngine(config->getEngineLibraryNameSuffix()),
                clientInterface);
    }
    if (apm->initialize() != NO_ERROR) {
        delete apm;
        return nullptr;
    }
    return apm;
}

extern "C" void destroyAudioPolicyManager(AudioPolicyInterface *interface) {
    delete interface;
}

} // namespace android
