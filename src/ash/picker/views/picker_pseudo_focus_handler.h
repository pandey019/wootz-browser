// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ASH_PICKER_VIEWS_PICKER_PSEUDO_FOCUS_HANDLER_H_
#define ASH_PICKER_VIEWS_PICKER_PSEUDO_FOCUS_HANDLER_H_

#include "ash/ash_export.h"

namespace ash {

// Interface for classes that have pseudo focusable elements, which can look and
// behave as if they were focused without having actual focus. We use "pseudo
// focus" since actual view focus generally stays on the Picker search field,
// which just forwards user actions to be handled by pseudo focused elements if
// needed (e.g. to select an item when the user presses the enter key).
class ASH_EXPORT PickerPseudoFocusHandler {
 public:
  // Direction to traverse pseudo focusable elements.
  enum class PseudoFocusDirection {
    kForward,
    kBackward,
  };

  virtual ~PickerPseudoFocusHandler() = default;

  // Returns true if an action was performed.
  virtual bool DoPseudoFocusedAction() = 0;

  // Returns true if pseudo focus was moved to a different element.
  virtual bool MovePseudoFocusUp() = 0;
  virtual bool MovePseudoFocusDown() = 0;
  virtual bool MovePseudoFocusLeft() = 0;
  virtual bool MovePseudoFocusRight() = 0;

  // Moves pseudo focus to the next (or previous) pseudo focusable element.
  virtual void AdvancePseudoFocus(PseudoFocusDirection direction) = 0;
};

}  // namespace ash

#endif  // ASH_PICKER_VIEWS_PICKER_PSEUDO_FOCUS_HANDLER_H_
