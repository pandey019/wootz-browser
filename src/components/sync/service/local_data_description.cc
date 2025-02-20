// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "components/sync/service/local_data_description.h"

namespace syncer {

LocalDataDescription::LocalDataDescription() = default;

LocalDataDescription::LocalDataDescription(const LocalDataDescription&) =
    default;

LocalDataDescription& LocalDataDescription::operator=(
    const LocalDataDescription&) = default;

LocalDataDescription::LocalDataDescription(LocalDataDescription&&) = default;

LocalDataDescription& LocalDataDescription::operator=(LocalDataDescription&&) =
    default;

LocalDataDescription::~LocalDataDescription() = default;

void PrintTo(const LocalDataDescription& desc, std::ostream* os) {
  *os << "{ type:" << syncer::ModelTypeToDebugString(desc.type)
      << ", item_count:" << desc.item_count << ", domains:[";
  for (const auto& domain : desc.domains) {
    *os << domain << ",";
  }
  *os << "], domain_count:" << desc.domain_count;
}

}  // namespace syncer
