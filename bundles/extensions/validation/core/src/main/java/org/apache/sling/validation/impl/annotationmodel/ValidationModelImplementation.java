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
package org.apache.sling.validation.impl.annotationmodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.apache.sling.validation.model.ValidationModel;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Validation model implementation.
 * It keeps all registered validation models.
 */
final class ValidationModelImplementation {

    private static final Logger log = LoggerFactory.getLogger(ValidationModelImplementation.class);

    private final ConcurrentMap<Bundle, ConcurrentHashMap<String, List<ValidationModel>>> validationModels = new ConcurrentHashMap<>();

    /**
     * Remove all implementation mappings.
     */
    void removeAll() {
        validationModels.clear();
    }

    /**
     * Gets validation models.
     *
     * @param bundle the bundle
     * @return the validation models
     */
    @Nonnull
    ConcurrentMap<String, List<ValidationModel>> getValidationModels(final Bundle bundle) {
        return validationModels.getOrDefault(bundle, new ConcurrentHashMap<>());
    }

    /**
     * Gets validation models by resource type.
     *
     * @param resourceType the resource type
     * @return the validation models by resource type
     */
    @Nonnull
    List<ValidationModel> getValidationModelsByResourceType(String resourceType) {
        return validationModels.entrySet()
                .parallelStream()
                .map(Map.Entry::getValue)
                .filter(setMap -> setMap.containsKey(resourceType))
                .map(setMap -> setMap.get(resourceType))
                .map(ArrayList<ValidationModel>::new)
                .collect(ArrayList::new, ArrayList::addAll, ArrayList::addAll);
    }

    /**
     * Remove validation models.
     *
     * @param bundle the bundle
     */
    void removeValidationModels(Bundle bundle) {
        validationModels.remove(bundle);
    }

    /**
     * Register validation models by bundle.
     *
     * @param bundle the bundle
     * @param models the models
     */
    void registerValidationModelsByBundle(final Bundle bundle, final Collection<ValidationModel> models) {
        models.forEach(model -> registerValidationModelByBundle(bundle, model));
    }

    private void registerValidationModelByBundle(final Bundle bundle, final ValidationModel model) {

        ConcurrentHashMap<String, List<ValidationModel>> map = Optional.ofNullable(validationModels.get(bundle))
                .orElseGet(supplyConcurrentHashMap(bundle));

        String resourceType = model.getValidatingResourceType();
        Optional.ofNullable(map.get(resourceType))
                .orElseGet(supplyList(map, resourceType))
                .add(model);
    }

    private Supplier<List<ValidationModel>> supplyList(ConcurrentHashMap<String, List<ValidationModel>> map, String resourceType) {
        return () -> {
            List<ValidationModel> validationModels = new ArrayList<>();
            map.put(resourceType, validationModels);
            return validationModels;
        };
    }

    private Supplier<ConcurrentHashMap<String, List<ValidationModel>>> supplyConcurrentHashMap(Bundle bundle) {
        return () -> {
            ConcurrentHashMap<String, List<ValidationModel>> cmp = new ConcurrentHashMap<>();
            validationModels.put(bundle, cmp);
            return cmp;
        };
    }
}
