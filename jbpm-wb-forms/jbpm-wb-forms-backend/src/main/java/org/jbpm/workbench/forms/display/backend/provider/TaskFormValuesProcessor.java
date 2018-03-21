/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.workbench.forms.display.backend.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.jbpm.workbench.forms.service.providing.TaskRenderingSettings;
import org.jbpm.workbench.forms.service.providing.model.TaskDefinition;
import org.kie.workbench.common.forms.dynamic.service.context.generation.dynamic.BackendFormRenderingContext;
import org.kie.workbench.common.forms.dynamic.service.context.generation.dynamic.BackendFormRenderingContextManager;
import org.kie.workbench.common.forms.dynamic.service.shared.RenderMode;
import org.kie.workbench.common.forms.fields.shared.fieldTypes.basic.textArea.type.TextAreaFieldType;
import org.kie.workbench.common.forms.jbpm.model.authoring.task.TaskFormModel;
import org.kie.workbench.common.forms.jbpm.service.bpmn.DynamicBPMNFormGenerator;
import org.kie.workbench.common.forms.jbpm.service.bpmn.util.BPMNVariableUtils;
import org.kie.workbench.common.forms.model.FormDefinition;
import org.kie.workbench.common.forms.model.ModelProperty;
import org.kie.workbench.common.forms.model.impl.meta.entries.FieldReadOnlyEntry;
import org.kie.workbench.common.forms.model.impl.meta.entries.FieldTypeEntry;
import org.kie.workbench.common.forms.service.backend.util.ModelPropertiesGenerator;
import org.kie.workbench.common.forms.services.backend.serialization.FormDefinitionSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dependent
public class TaskFormValuesProcessor extends KieWorkbenchFormsValuesProcessor<TaskRenderingSettings> {

    private static final Logger logger = LoggerFactory.getLogger(TaskFormValuesProcessor.class);

    @Inject
    public TaskFormValuesProcessor(FormDefinitionSerializer formSerializer,
                                   BackendFormRenderingContextManager contextManager,
                                   DynamicBPMNFormGenerator dynamicBPMNFormGenerator) {
        super(formSerializer,
              contextManager,
              dynamicBPMNFormGenerator);
    }

    @Override
    protected String getFormName(TaskRenderingSettings settings) {
        return settings.getTask().getFormName();
    }

    @Override
    protected Map<String, Object> getOutputValues(Map<String, Object> values,
                                                  FormDefinition form,
                                                  TaskRenderingSettings settings) {
        if (isValid(form)) {

            TaskDefinition task = settings.getTask();

            // Removing task inputs
            task.getTaskInputDefinitions().keySet().forEach(key -> {
                if (!task.getTaskOutputDefinitions().containsKey(key)) {
                    values.remove(key);
                }
            });

            return values;
        }
        throw new IllegalArgumentException("Form not valid for task");
    }

    @Override
    protected void prepareContext(TaskRenderingSettings settings,
                                  BackendFormRenderingContext context) {
        context.getRenderingContext().setRenderMode(!"InProgress".equals(settings.getTask().getStatus()) ? RenderMode.READ_ONLY_MODE : RenderMode.EDIT_MODE);
    }

    @Override
    protected Map<String, Object> generateRawFormData(TaskRenderingSettings settings,
                                                      FormDefinition form) {

        final Map<String, Object> formData = new HashMap<>();

        if (isValid(form)) {

            Map<String, Object> inputs = settings.getInputs();
            Map<String, Object> outputs = settings.getOutputs();

            formData.putAll(inputs);
            if (settings.getTask().isOutputIncluded()) {
                formData.putAll(outputs);
            }
        }
        return formData;
    }

    @Override
    protected Collection<FormDefinition> generateDefaultFormsForContext(TaskRenderingSettings settings) {
        List<ModelProperty> properties = new ArrayList<>();

        TaskDefinition task = settings.getTask();

        Map<String, String> taskData = new HashMap<>();
        taskData.putAll(task.getTaskInputDefinitions());
        taskData.putAll(task.getTaskOutputDefinitions());

        taskData.forEach((name, type) -> {
            // filter not needed variables
            if (BPMNVariableUtils.isValidInputName(name)) {
                ModelProperty property = ModelPropertiesGenerator.createModelProperty(name,
                                                                                      BPMNVariableUtils.getRealTypeForInput(type),
                                                                                      settings.getMarshallerContext().getClassloader());
                if (task.getTaskInputDefinitions().containsKey(name) && !task.getTaskOutputDefinitions().containsKey(name)) {
                    property.getMetaData().addEntry(new FieldReadOnlyEntry(true));
                }

                if (property.getTypeInfo().getClassName().equals(Object.class.getName())) {
                    property.getMetaData().addEntry(new FieldTypeEntry(TextAreaFieldType.NAME));
                }

                properties.add(property);
            }
        });

        TaskFormModel formModel = new TaskFormModel(task.getProcessId(),
                                                    task.getFormName(),
                                                    properties.stream().sorted((variable1, variable2) -> {
                                                        boolean variable1OnlyInput = task.getTaskInputDefinitions().containsKey(variable1.getName()) && !task.getTaskOutputDefinitions().containsKey(variable1.getName());
                                                        boolean variable2OnlyInput = task.getTaskInputDefinitions().containsKey(variable2.getName()) && !task.getTaskOutputDefinitions().containsKey(variable2.getName());

                                                        if (variable1OnlyInput) {
                                                            if (variable2OnlyInput) {
                                                                return variable1.getName().compareToIgnoreCase(variable2.getName());
                                                            } else {
                                                                return -1;
                                                            }
                                                        }

                                                        if (variable2OnlyInput) {
                                                            return 1;
                                                        }

                                                        return variable1.getName().compareToIgnoreCase(variable2.getName());
                                                    }).collect(Collectors.toList()));

        Collection<FormDefinition> forms = dynamicBPMNFormGenerator.generateTaskForms(formModel,
                                                                                      settings.getMarshallerContext().getClassloader());

        // Determinging what variables are only on the input map, so we can set them as readonly
        taskData.keySet().removeAll(task.getTaskOutputDefinitions().keySet());

        boolean done = false;
        for (Iterator<FormDefinition> it = forms.iterator(); it.hasNext() && !done; ) {
            FormDefinition form = it.next();

            if (form.getName().equals(getFormName(settings) + BPMNVariableUtils.TASK_FORM_SUFFIX)) {
                form.getFields().forEach(field -> {
                    field.setReadOnly(taskData.containsKey(field.getBinding()));
                });
                done = true;
            }
        }

        return forms;
    }

    @Override
    protected boolean isValid(FormDefinition rootForm) {
        return rootForm != null && rootForm.getModel() instanceof TaskFormModel;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }
}