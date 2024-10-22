/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.nfc.cardemulation;

import android.annotation.UserIdInt;
import android.content.ComponentName;
import java.util.Objects;
import android.util.Pair;

 public class ComponentNameAndUser {
    @UserIdInt
    private final int mUserId;
    private ComponentName mComponentName;

    ComponentNameAndUser(@UserIdInt int userId, ComponentName componentName) {
        mUserId = userId;
        mComponentName = componentName;
    }

    static ComponentNameAndUser create(Pair<Integer, ComponentName> pair) {
        if (pair == null) {
            return null;
        }
        return new ComponentNameAndUser(pair.first == null ? -1 : pair.first, pair.second);
    }

    @UserIdInt int getUserId() {
        return mUserId;
    }

    ComponentName getComponentName() {
        return mComponentName;
    }

    @Override
    public String toString() {
        return mComponentName + " for user id: " + mUserId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof ComponentNameAndUser) {
            ComponentNameAndUser other = (ComponentNameAndUser)obj;
            return other.getUserId() == mUserId &&
                    Objects.equals(other.getComponentName(), mComponentName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (mComponentName == null) {
            return mUserId;
        }
        return mComponentName.hashCode() + mUserId;
    }
}