// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "media/cast/encoding/encoding_support.h"

#include <stdint.h>

#include "base/no_destructor.h"
#include "build/build_config.h"
#include "media/base/media_switches.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace media::cast::encoding_support {
namespace {

std::vector<media::VideoEncodeAccelerator::SupportedProfile>
GetValidProfiles() {
  static const base::NoDestructor<
      std::vector<media::VideoEncodeAccelerator::SupportedProfile>>
      kValidProfiles({
          VideoEncodeAccelerator::SupportedProfile(media::VP8PROFILE_MIN,
                                                   gfx::Size(1920, 1080)),
          VideoEncodeAccelerator::SupportedProfile(media::H264PROFILE_MIN,
                                                   gfx::Size(1920, 1080)),
      });

  return *kValidProfiles;
}

}  // namespace

TEST(EncodingSupportTest, EnablesVp8HardwareEncoderAlways) {
  EXPECT_TRUE(IsHardwareEnabled(VideoCodec::kVP8, GetValidProfiles()));
}

TEST(EncodingSupportTest, EnablesH264HardwareEncoderProperly) {
  static const bool is_enabled =
#if BUILDFLAG(IS_MAC)
      base::FeatureList::IsEnabled(kCastStreamingMacHardwareH264);

// The hardware encoder is broken on Windows.
#elif BUILDFLAG(IS_WIN)
      false;
#else
      true;
#endif

  EXPECT_EQ(is_enabled,
            IsHardwareEnabled(VideoCodec::kH264, GetValidProfiles()));
}

}  // namespace media::cast::encoding_support
