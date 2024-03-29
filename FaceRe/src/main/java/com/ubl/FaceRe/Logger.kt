/*
 * Copyright 2022 Sushant Gautam
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ubl.FaceRe

// Logs message using log_textview present in activity_main.xml
class Logger {

    companion object {

        fun log(message: String) {
            FaceReActivity.setMessage(FaceReActivity.logTextView.text.toString() + "\n" + ">> $message")
            // To scroll to the last message
            // See this SO answer -> https://stackoverflow.com/a/37806544/10878733
            FaceReActivity.checkCanScrollVertically()


        }

    }

}