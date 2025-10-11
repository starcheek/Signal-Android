/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import androidx.lifecycle.MutableLiveData

object Translation {
  val translationLangStateFlow: MutableLiveData<String?> = MutableLiveData(null)
  val languages = arrayOf("France", "Canadian", "English", "Arabic")
  val languagesCodes = arrayOf("fr", "en-CA", "en", "ar")
}