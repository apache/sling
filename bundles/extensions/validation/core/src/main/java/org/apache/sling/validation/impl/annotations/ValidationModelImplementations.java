/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.validation.impl.annotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.sling.validation.model.ValidationModel;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author karolis.mackevicius@netcentric.biz
 * @since 02/04/17 */
final class ValidationModelImplementations {

    private static final Logger log = LoggerFactory.getLogger(ValidationModelImplementations.class);

    private final ConcurrentMap<Bundle, Map<String, ConcurrentSkipListSet<ValidationModel>>> validationModels = new ConcurrentHashMap<>();

    /** Remove all implementation mappings. */
    public void removeAll() {
        validationModels.clear();
    }

    public void registerValidationModelByBundle(final Bundle bundle, String resourceType, final ValidationModel model) {
        Map<String, ConcurrentSkipListSet<ValidationModel>> map = validationModels.get(bundle);
        ConcurrentSkipListSet<ValidationModel> models;
        if (map == null) {
            models = new ConcurrentSkipListSet(Collections.singleton(model));
            map = Collections.singletonMap(resourceType, models);
            validationModels.put(bundle, map);
        } else {
            models = map.getOrDefault(resourceType, new ConcurrentSkipListSet<>());
            models.add(model);
        }
    }

    public Map<String, ConcurrentSkipListSet<ValidationModel>> getValidationModels(final Bundle bundle) {
        return validationModels.getOrDefault(bundle, new ConcurrentHashMap<>());
    }

    public List<ValidationModel> getValidationModelsByResourceType(String resourceType) {
        return validationModels.entrySet()
                .parallelStream()
                .map(Map.Entry::getValue)
                .filter(setMap -> setMap.containsKey(resourceType))
                .map(setMap -> setMap.get(resourceType))
                .map(ArrayList<ValidationModel>::new)
                .collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll);
    }

    public void removeValidationModels(Bundle bundle) {
        validationModels.remove(bundle);
    }
}
