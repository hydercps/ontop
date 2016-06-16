package it.unibz.inf.ontop.protege.views;

/*
 * #%L
 * ontop-protege
 * %%
 * Copyright (C) 2009 - 2013 KRDB Research Centre. Free University of Bozen Bolzano.
 * %%
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
 * #L%
 */

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import it.unibz.inf.ontop.protege.core.OBDAModelManager;
<<<<<<< HEAD:ontop-protege/src/main/java/it/unibz/inf/ontop/protege/views/DatasourceParametersEditorView.java
import it.unibz.inf.ontop.protege.core.OBDAModelManagerListener;
import it.unibz.inf.ontop.protege.panels.DatasourceParameterEditorPanel;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import it.unibz.inf.ontop.model.impl.OBDAModelImpl;
=======
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import it.unibz.inf.ontop.model.impl.OBDAModelImpl;
import it.unibz.inf.ontop.protege.core.OBDAModelManagerListener;
import it.unibz.inf.ontop.protege.panels.DatasourceParameterEditorPanel;
>>>>>>> v3/package-names-changed:ontop-protege/src/main/java/it/unibz/inf/ontop/protege/views/DatasourceParametersEditorView.java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasourceParametersEditorView extends AbstractOWLViewComponent implements OBDAModelManagerListener {

	private static final long serialVersionUID = 1L;
	
	private static final Logger log = LoggerFactory.getLogger(DatasourceParametersEditorView.class);

	private DatasourceParameterEditorPanel panel;

	private OBDAModelManager apic;

	@Override
	protected void disposeOWLView() {
		apic.removeListener(this);
	}

	@Override
	protected void initialiseOWLView() throws Exception {
		
		
		
		
		apic = (OBDAModelManager) getOWLEditorKit().get(OBDAModelImpl.class.getName());
		apic.addListener(this);

		panel = new DatasourceParameterEditorPanel(apic.getActiveOBDAModelWrapper());
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		
		setLayout(new GridBagLayout());
		GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 1;
        gridBagConstraints.weighty = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        add(panel, gridBagConstraints);
        
//		add(panel, BorderLayout.CENTER);
		
		log.debug("Datasource parameter view Component initialized");
	}

	@Override
	public void activeOntologyChanged() {
		panel.setDatasourcesController(apic.getActiveOBDAModelWrapper());
	}
}
