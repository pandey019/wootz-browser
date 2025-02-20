// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ash/chromebox_for_meetings/hotlog2/log_file.h"

#include <unistd.h>

#include <fstream>

#include "base/logging.h"
#include "base/threading/scoped_blocking_call.h"

namespace ash::cfm {

LogFile::LogFile(const std::string& filepath) : filepath_(filepath) {}

LogFile::~LogFile() {
  CloseStream();
}

const std::string& LogFile::GetFilePath() const {
  return filepath_.value();
}

bool LogFile::OpenAtOffset(std::streampos offset) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  if (access(GetFilePath().c_str(), F_OK) != 0) {
    LOG(ERROR) << "File " << filepath_ << " doesn't exist";
    return false;
  }

  if (access(GetFilePath().c_str(), R_OK) != 0) {
    LOG(ERROR) << "Not enough permissions on file " << filepath_;
    return false;
  }

  file_stream_ = std::ifstream(GetFilePath(), std::ios::in);
  file_stream_.seekg(offset);

  if (IsInFailState()) {
    LOG(ERROR) << "Unable to seek to offset " << offset << " in file "
               << filepath_;
    return false;
  }

  return true;
}

void LogFile::CloseStream() {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);
  file_stream_.close();
}

bool LogFile::IsInFailState() const {
  return file_stream_.bad();
}

bool LogFile::IsAtEOF() const {
  return file_stream_.eof() && !IsInFailState();
}

std::streampos LogFile::GetCurrentOffset() {
  std::streampos offset;

  // tellg() will report -1 if the last read resulted in an EOF,
  // but we want the true offset value. Temporarily clear the EOF
  // bit, store the tellg() result, and call .get() to trigger
  // another EOF. Note there is no risk of .get() advancing our
  // buffer accidentally as changes on the filesystem will not
  // be reflected in our stream until we do a full close & open.
  if (IsAtEOF()) {
    file_stream_.clear();
    offset = file_stream_.tellg();
    file_stream_.get();
  } else {
    offset = file_stream_.tellg();
  }

  return offset;
}

bool LogFile::Refresh() {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  std::streampos curr_pos = GetCurrentOffset();
  CloseStream();
  return OpenAtOffset(curr_pos);
}

std::vector<std::string> LogFile::RetrieveNextLogs(size_t count) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  std::vector<std::string> logs;
  size_t num_read_lines = 0;

  std::string line;
  while (!IsAtEOF() && !IsInFailState() && num_read_lines < count &&
         std::getline(file_stream_, line)) {
    logs.push_back(std::move(line));
    num_read_lines++;
  }

  if (IsInFailState()) {
    LOG(ERROR) << "Error reading file " << filepath_ << " after "
               << num_read_lines << " lines";
  }

  return logs;
}

}  // namespace ash::cfm
