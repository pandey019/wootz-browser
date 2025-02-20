// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/quick_answers/ui/loading_view.h"

#include "chromeos/strings/grit/chromeos_strings.h"
#include "ui/base/l10n/l10n_util.h"
#include "ui/base/metadata/metadata_impl_macros.h"
#include "ui/color/color_id.h"
#include "ui/views/controls/label.h"
#include "ui/views/layout/box_layout_view.h"
#include "ui/views/view_utils.h"

namespace quick_answers {
namespace {
constexpr int kLineSpacingDip = 4;
}

LoadingView::LoadingView() {
  SetOrientation(views::LayoutOrientation::kVertical);
  SetCrossAxisAlignment(views::LayoutAlignment::kStart);
  SetDefault(views::kMarginsKey, gfx::Insets::TLBR(0, 0, kLineSpacingDip, 0));

  first_line_label_.SetView(AddChildView(
      views::Builder<views::Label>()
          .SetEnabledColor(ui::kColorLabelForeground)
          // Default is `ALIGN_CENTER`. See `Label::Init`.
          // `SetHorizontalAlignment` flips the value for RTL.
          .SetHorizontalAlignment(gfx::HorizontalAlignment::ALIGN_LEFT)
          .SetProperty(
              views::kFlexBehaviorKey,
              views::FlexSpecification(views::MinimumFlexSizeRule::kScaleToZero,
                                       views::MaximumFlexSizeRule::kPreferred))
          .Build()));

  AddChildView(
      views::Builder<views::Label>()
          .SetText(l10n_util::GetStringUTF16(IDS_QUICK_ANSWERS_VIEW_LOADING))
          .SetEnabledColor(ui::kColorLabelForegroundSecondary)
          .SetHorizontalAlignment(gfx::HorizontalAlignment::ALIGN_LEFT)
          .Build());
}

void LoadingView::SetFirstLineText(const std::u16string& first_line_text) {
  views::Label* label = AsViewClass<views::Label>(first_line_label_.view());
  CHECK(label);
  label->SetText(first_line_text);
}

std::u16string LoadingView::GetFirstLineText() const {
  const views::Label* label =
      AsViewClass<views::Label>(first_line_label_.view());
  CHECK(label);
  return label->GetText();
}

BEGIN_METADATA(LoadingView)
ADD_PROPERTY_METADATA(std::u16string, FirstLineText)
END_METADATA

}  // namespace quick_answers
