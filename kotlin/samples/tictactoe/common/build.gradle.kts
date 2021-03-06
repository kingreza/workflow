/*
 * Copyright 2017 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-ui:backstack-common"))
  implementation(project(":workflow-ui:modal-common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))

  implementation(Dependencies.Kotlin.Stdlib.jdk6)
  implementation(Dependencies.RxJava2.rxjava2)

  testImplementation(Dependencies.Test.hamcrestCore)
  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(Dependencies.RxJava2.extensions)
  testImplementation(project(":workflow-testing"))
}
