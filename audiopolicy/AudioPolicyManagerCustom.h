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

#pragma once

#include <managerdefault/AudioPolicyManager.h>

namespace android {

class AudioPolicyManagerCustom : public AudioPolicyManager {
public:
    AudioPolicyManagerCustom(const sp<const AudioPolicyConfig>& config,
                             EngineInstance&& engine,
                             AudioPolicyClientInterface *clientInterface);
    virtual ~AudioPolicyManagerCustom() = default;

    status_t getOutputForAttr(const audio_attributes_t *attr,
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
                              bool *isBitPerfect) override;
};

} // namespace android
