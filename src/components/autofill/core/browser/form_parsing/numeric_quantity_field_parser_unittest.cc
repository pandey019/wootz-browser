// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "components/autofill/core/browser/form_parsing/numeric_quantity_field_parser.h"

#include "components/autofill/core/browser/form_parsing/parsing_test_utils.h"

namespace autofill {

class NumericQuantityFieldParserTest
    : public FormFieldParserTestBase,
      public testing::TestWithParam<PatternProviderFeatureState> {
 public:
  explicit NumericQuantityFieldParserTest()
      : FormFieldParserTestBase(GetParam()) {}
  NumericQuantityFieldParserTest(const NumericQuantityFieldParserTest&) =
      delete;
  NumericQuantityFieldParserTest& operator=(
      const NumericQuantityFieldParserTest&) = delete;

 protected:
  std::unique_ptr<FormFieldParser> Parse(ParsingContext& context,
                                         AutofillScanner* scanner) override {
    return NumericQuantityFieldParser::Parse(context, scanner);
  }
};

INSTANTIATE_TEST_SUITE_P(
    NumericQuantityFieldParserTest,
    NumericQuantityFieldParserTest,
    ::testing::ValuesIn(PatternProviderFeatureState::All()));

TEST_P(NumericQuantityFieldParserTest, ParseNumericQuantity) {
  AddTextFormFieldData("quantity", "quantity", NUMERIC_QUANTITY);

  ClassifyAndVerify(ParseResult::kParsed);
}

TEST_P(NumericQuantityFieldParserTest, ParseNonNumericQuantity) {
  AddTextFormFieldData("name", "Name", UNKNOWN_TYPE);

  ClassifyAndVerify(ParseResult::kNotParsed);
}

}  // namespace autofill
