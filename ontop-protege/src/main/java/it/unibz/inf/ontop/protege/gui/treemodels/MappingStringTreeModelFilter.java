package it.unibz.inf.ontop.protege.gui.treemodels;

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

import java.util.List;

<<<<<<< HEAD:ontop-protege/src/main/java/it/unibz/inf/ontop/protege/gui/treemodels/MappingStringTreeModelFilter.java
import it.unibz.inf.ontop.model.CQIE;
import it.unibz.inf.ontop.model.Function;
import it.unibz.inf.ontop.model.OBDAMappingAxiom;
import it.unibz.inf.ontop.model.OBDASQLQuery;
import it.unibz.inf.ontop.model.impl.CQIEImpl;
=======
import it.unibz.inf.ontop.model.Function;
import it.unibz.inf.ontop.model.OBDAMappingAxiom;
import it.unibz.inf.ontop.model.impl.CQIEImpl;
import it.unibz.inf.ontop.model.CQIE;
import it.unibz.inf.ontop.model.OBDASQLQuery;
>>>>>>> v3/package-names-changed:ontop-protege/src/main/java/it/unibz/inf/ontop/protege/gui/treemodels/MappingStringTreeModelFilter.java

/**
 * This filter receives a string in the constructor and returns true if accepts
 * any mapping containing the string in the head or body
 */
public class MappingStringTreeModelFilter extends TreeModelFilter<OBDAMappingAxiom> {

	public MappingStringTreeModelFilter() {
		super.bNegation = false;
	}

	@Override
	public boolean match(OBDAMappingAxiom object) {
		boolean isMatch = false;
		for (String keyword : vecKeyword) {
			// Check in the Mapping ID
			final String mappingId = object.getId();
			isMatch = MappingIDTreeModelFilter.match(keyword.trim(), mappingId);
			if (isMatch) {
				break; // end loop if a match is found!
			}

			// Check in the Mapping Target Query
			final CQIE headquery = (CQIEImpl) object.getTargetQuery();
			final List<Function> atoms = headquery.getBody();
			for (int i = 0; i < atoms.size(); i++) {
				Function predicate = (Function) atoms.get(i);
				isMatch = isMatch || MappingHeadVariableTreeModelFilter.match(keyword.trim(), predicate);
			}
			if (isMatch) {
				break; // end loop if a match is found!
			}

			// Check in the Mapping Source Query
			final OBDASQLQuery query = (OBDASQLQuery) object.getSourceQuery();
			isMatch = MappingSQLStringTreeModelFilter.match(keyword.trim(), query.toString());
			if (isMatch) {
				break; // end loop if a match is found!
			}
		}
		// no match found!
		return (bNegation ? !isMatch : isMatch);
	}
}
