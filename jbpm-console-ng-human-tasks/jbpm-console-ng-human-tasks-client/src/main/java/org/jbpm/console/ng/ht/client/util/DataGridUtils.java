/*
 * Copyright 2013 JBoss by Red Hat.
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

package org.jbpm.console.ng.ht.client.util;

import org.jbpm.console.ng.ht.model.TaskSummary;

import com.github.gwtbootstrap.client.ui.DataGrid;

public class DataGridUtils {

    private static final String BG_ROW_SELECTED = "#E5F1FF";
	
    private static final String BG_ROW_COMPLETED = "#881100";
	
    private static final String COMPLETED = "Completed";


    public static void paintRowSelected(DataGrid<TaskSummary> myTaskListGrid, Long idTask) {
        for (int i = 0; i < myTaskListGrid.getRowCount(); i++) {
            for (int j = 0; j < myTaskListGrid.getColumnCount(); j++) {
                if (!Long.valueOf(myTaskListGrid.getRowElement(i).getCells().getItem(0).getInnerText()).equals(idTask)) {
                    myTaskListGrid.getRowElement(i).getCells().getItem(j).getStyle().clearBackgroundColor();
                } else {
                    myTaskListGrid.getRowElement(i).getCells().getItem(j).getStyle().setBackgroundColor(BG_ROW_SELECTED);
                }
            }
        }
    }
    
    
}