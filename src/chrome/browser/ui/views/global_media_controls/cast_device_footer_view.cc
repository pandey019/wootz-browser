// Copyright 2024 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/ui/views/global_media_controls/cast_device_footer_view.h"

#include "chrome/browser/ui/views/global_media_controls/media_item_ui_helper.h"
#include "components/strings/grit/components_strings.h"
#include "components/vector_icons/vector_icons.h"
#include "ui/base/l10n/l10n_util.h"
#include "ui/base/metadata/metadata_impl_macros.h"
#include "ui/views/background.h"
#include "ui/views/controls/button/label_button.h"
#include "ui/views/layout/box_layout.h"

namespace {

constexpr gfx::Size kPreferredSize{370, 0};

constexpr int kBackgroundBorderThickness = 1;
constexpr int kBackgroundCornerRadius = 8;
constexpr int kStopCastingButtonCornerRadius = 10;
constexpr int kBackgroundSeparator = 8;
constexpr int kStopCastingButtonSeparator = 6;
constexpr int kDeviceIconSize = 20;
constexpr int kStopCastingButtonIconSize = 12;

constexpr gfx::Insets kBackgroundInsets = gfx::Insets::VH(14, 16);

}  // namespace

CastDeviceFooterView::CastDeviceFooterView(
    base::RepeatingClosure stop_casting_callback,
    media_message_center::MediaColorTheme media_color_theme)
    : stop_casting_callback_(std::move(stop_casting_callback)) {
  SetPreferredSize(kPreferredSize);
  SetBorder(views::CreateThemedRoundedRectBorder(
      kBackgroundBorderThickness, kBackgroundCornerRadius,
      media_color_theme.device_selector_border_color_id));
  SetBackground(views::CreateThemedRoundedRectBackground(
      media_color_theme.device_selector_background_color_id,
      kBackgroundCornerRadius));
  auto* layout = SetLayoutManager(std::make_unique<views::BoxLayout>(
      views::BoxLayout::Orientation::kHorizontal, kBackgroundInsets,
      kBackgroundSeparator));

  // Add the device icon.
  device_icon_ = AddChildView(
      std::make_unique<views::ImageView>(ui::ImageModel::FromVectorIcon(
          vector_icons::kMediaRouterIdleIcon,
          media_color_theme.device_selector_foreground_color_id,
          kDeviceIconSize)));

  // Add the device name.
  // TODO(yrw): Add in the correct device name.
  device_name_ = AddChildView(std::make_unique<views::Label>(u"Test Device"));
  device_name_->SetTextStyle(views::style::STYLE_BODY_2);
  device_name_->SetEnabledColorId(
      media_color_theme.device_selector_foreground_color_id);
  device_name_->SetHorizontalAlignment(gfx::ALIGN_LEFT);
  layout->SetFlexForView(device_name_, 1);

  // Add the stop casting button.
  stop_casting_button_ = AddChildView(std::make_unique<views::LabelButton>(
      base::BindRepeating(&CastDeviceFooterView::StopCasting,
                          base::Unretained(this)),
      l10n_util::GetStringUTF16(
          IDS_MEDIA_MESSAGE_CENTER_MEDIA_NOTIFICATION_ACTION_STOP_CASTING)));
  stop_casting_button_->SetImageModel(
      views::Button::STATE_NORMAL,
      ui::ImageModel::FromVectorIcon(
          vector_icons::kStopCircleIcon,
          media_color_theme.error_foreground_color_id,
          kStopCastingButtonIconSize));
  stop_casting_button_->SetLabelStyle(views::style::STYLE_BODY_5);
  stop_casting_button_->SetEnabledTextColorIds(
      media_color_theme.error_foreground_color_id);
  stop_casting_button_->SetImageLabelSpacing(kStopCastingButtonSeparator);
  stop_casting_button_->SetBackground(views::CreateThemedRoundedRectBackground(
      media_color_theme.error_container_color_id,
      kStopCastingButtonCornerRadius));

  // Set the focus behavior for stop casting button.
  stop_casting_button_->SetFocusBehavior(views::View::FocusBehavior::ALWAYS);
  stop_casting_button_->SetFocusRingCornerRadius(
      kStopCastingButtonCornerRadius);
  views::FocusRing::Get(stop_casting_button_)
      ->SetColorId(media_color_theme.focus_ring_color_id);
}

CastDeviceFooterView::~CastDeviceFooterView() = default;

views::LabelButton* CastDeviceFooterView::GetStopCastingButtonForTesting() {
  return stop_casting_button_;
}

void CastDeviceFooterView::StopCasting() {
  stop_casting_button_->SetEnabled(false);
  stop_casting_callback_.Run();
}

BEGIN_METADATA(CastDeviceFooterView)
END_METADATA
