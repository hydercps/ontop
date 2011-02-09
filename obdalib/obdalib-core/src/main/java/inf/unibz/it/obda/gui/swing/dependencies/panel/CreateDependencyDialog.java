/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package inf.unibz.it.obda.gui.swing.dependencies.panel;

import inf.unibz.it.obda.api.controller.APIController;
import inf.unibz.it.obda.api.controller.AssertionController;
import inf.unibz.it.obda.api.controller.DatasourcesController;
import inf.unibz.it.obda.api.controller.MappingController;
import inf.unibz.it.obda.dependencies.controller.RDBMSDisjointnessDependencyController;
import inf.unibz.it.obda.dependencies.controller.RDBMSFunctionalDependencyController;
import inf.unibz.it.obda.dependencies.controller.RDBMSInclusionDependencyController;
import inf.unibz.it.obda.dependencies.domain.imp.RDBMSDisjointnessDependency;
import inf.unibz.it.obda.dependencies.domain.imp.RDBMSFunctionalDependency;
import inf.unibz.it.obda.dependencies.domain.imp.RDBMSInclusionDependency;
import inf.unibz.it.obda.domain.OBDAMappingAxiom;
import inf.unibz.it.obda.rdbmsgav.domain.RDBMSSQLQuery;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Vector;

import org.obda.query.domain.Variable;
import org.obda.query.domain.imp.TermFactoryImpl;

/*
 * CreateDependencyDialog.java
 *
 * Created on Aug 14, 2009, 9:30:25 AM
 */

/**
 *	A Dialog which allows the user to create new assertion automatically
 *	The user only has to select two mappings and insert the involved
 *	terms into the dialog.
 *
 * @author Manfred Gerstgrasser
 * 		   KRDB Research Center, Free University of Bolzano/Bozen, Italy
 */

public class CreateDependencyDialog extends javax.swing.JDialog {

	/**
	 * The API controller
	 */
	private APIController apic =null;
	/**
	 * The id of the first mapping
	 */
	private String idOfMapping1 = null;
	/**
	 * id of the second mapping
	 */
	private String idOfMapping2 = null;
	/**
	 * the name of the assertion which will be created
	 */
	private String assertion = null;

	private CreateDependencyDialog myself = null;

	private final TermFactoryImpl termFactory = TermFactoryImpl.getInstance();

    /** Creates new form CreateDependencyDialog */
    public CreateDependencyDialog(java.awt.Frame parent, boolean modal, APIController apic, String id1, String id2, String assertion) {
        super(parent, modal);
        this.apic = apic;
        this.assertion = assertion;
        myself = this;
        idOfMapping1 = id1;
        idOfMapping2 = id2;
        initComponents();
        jLabelMapping1.setText(id1+":");
        jLabelMapping2.setText(id2+":");
        jButtonCancel.addActionListener(new ActionListener(){

			public void actionPerformed(ActionEvent e) {
				myself.dispose();
			}

        });

        KeyListener k = new KeyListener(){

			public void keyPressed(KeyEvent e) {

				if(e.getKeyCode() == KeyEvent.VK_ENTER){
					createAssertion();
				}else if(e.getKeyCode() == KeyEvent.VK_ESCAPE){
					myself.dispose();
				}
			}

			public void keyReleased(KeyEvent e) {}

			public void keyTyped(KeyEvent e) {}

        };
        jTextFieldMap1.addKeyListener(k);
        jTextFieldMap2.addKeyListener(k);
        jLabelOutPut.setForeground(Color.RED.darker());
    }

    private boolean validateInput(){
    	String input1 = jTextFieldMap1.getText();
    	String input2 = jTextFieldMap2.getText();

    	 String[] variablesMap1 = jTextFieldMap1.getText().split(",");
         String[] variablesMap2 = jTextFieldMap2.getText().split(",");

    	if(input1.equals("") || input2.equals("")){
    		jLabelOutPut.setText("Please insert variables for both mappings.");
    		return false;
    	}else if(input1.contains("$") || input2.contains("$")){
    		jLabelOutPut.setText("Please remove $. The sign is inserted automatically when needed.");
    		return false;
    	}else if(variablesMap1.length != variablesMap2.length){
    		jLabelOutPut.setText("Please insert the same number of varaibals for each mapping.");
    		return false;
    	}else{
    		return true;
    	}
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanel1 = new javax.swing.JPanel();
        jLabelTitle = new javax.swing.JLabel();
        jLabelOutPut = new javax.swing.JLabel();
        jLabelMapping1 = new javax.swing.JLabel();
        jLabelMapping2 = new javax.swing.JLabel();
        jTextFieldMap1 = new javax.swing.JTextField();
        jTextFieldMap2 = new javax.swing.JTextField();
        jLabelMessage = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jButtonCreate = new javax.swing.JButton();
        jButtonCancel = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        getContentPane().setLayout(new java.awt.GridBagLayout());

        jPanel1.setLayout(new java.awt.GridBagLayout());

        jLabelTitle.setText("Insert the variables involved in the dependency separeted by a comma:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel1.add(jLabelTitle, gridBagConstraints);

        jLabelOutPut.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel1.add(jLabelOutPut, gridBagConstraints);

        jLabelMapping1.setText("Mapping 1 ");
        jLabelMapping1.setMaximumSize(new java.awt.Dimension(62, 20));
        jLabelMapping1.setMinimumSize(new java.awt.Dimension(62, 20));
        jLabelMapping1.setPreferredSize(new java.awt.Dimension(62, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.ipadx = 50;
        gridBagConstraints.ipady = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 0);
        jPanel1.add(jLabelMapping1, gridBagConstraints);

        jLabelMapping2.setText("Mapping 2");
        jLabelMapping2.setMaximumSize(new java.awt.Dimension(66, 20));
        jLabelMapping2.setMinimumSize(new java.awt.Dimension(66, 20));
        jLabelMapping2.setPreferredSize(new java.awt.Dimension(66, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.ipadx = 50;
        gridBagConstraints.ipady = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 0);
        jPanel1.add(jLabelMapping2, gridBagConstraints);

        jTextFieldMap1.setToolTipText("insert variables for Mapping 1 seperated by a comma");
        jTextFieldMap1.setMinimumSize(new java.awt.Dimension(10, 20));
        jTextFieldMap1.setPreferredSize(new java.awt.Dimension(10, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        jPanel1.add(jTextFieldMap1, gridBagConstraints);

        jTextFieldMap2.setToolTipText("insert varaibalbes for mapping 2 seperated by a comma");
        jTextFieldMap2.setMinimumSize(new java.awt.Dimension(10, 20));
        jTextFieldMap2.setPreferredSize(new java.awt.Dimension(77, 20));
        jTextFieldMap2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextFieldMap2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        jPanel1.add(jTextFieldMap2, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.weighty = 1.0;
        jPanel1.add(jLabelMessage, gridBagConstraints);

        jPanel2.setLayout(new java.awt.GridBagLayout());

        jButtonCreate.setText("Create");
        jButtonCreate.setMaximumSize(new java.awt.Dimension(75, 22));
        jButtonCreate.setMinimumSize(new java.awt.Dimension(75, 22));
        jButtonCreate.setPreferredSize(new java.awt.Dimension(75, 22));
        jButtonCreate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createAssertion();
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipady = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel2.add(jButtonCreate, gridBagConstraints);

        jButtonCancel.setText("Cancel");
        jButtonCancel.setMaximumSize(new java.awt.Dimension(75, 22));
        jButtonCancel.setMinimumSize(new java.awt.Dimension(75, 22));
        jButtonCancel.setPreferredSize(new java.awt.Dimension(75, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipady = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        jPanel2.add(jButtonCancel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        jPanel2.add(jLabel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        jPanel1.add(jPanel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 3);
        getContentPane().add(jPanel1, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextFieldMap2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextFieldMap2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextFieldMap2ActionPerformed

    /**
     * When the create button is clicked, the input is read from the
     * text fields and a new assertion is added
     * @param evt
     */
    private void createAssertion() {//GEN-FIRST:event_jButtonCreateActionPerformed
        String[] variablesMap1 = jTextFieldMap1.getText().split(",");
        String[] variablesMap2 = jTextFieldMap2.getText().split(",");

        if(validateInput()){
        	MappingController mapcon = apic.getMappingController();
        	DatasourcesController dscon = apic.getDatasourcesController();
        	OBDAMappingAxiom map1 = mapcon.getMapping(dscon.getCurrentDataSource().getSourceID(), idOfMapping1);
        	OBDAMappingAxiom map2 = mapcon.getMapping(dscon.getCurrentDataSource().getSourceID(), idOfMapping2);
        	Vector<Variable> termOfM1 = new Vector<Variable>();
        	Vector<Variable> termOfM2 = new Vector<Variable>();
        	for(int i=0;i<variablesMap1.length;i++){
        		termOfM1.add(termFactory.createVariable(variablesMap1[i]));
        		termOfM2.add(termFactory.createVariable(variablesMap2[i]));
        	}
        	if(assertion.equals(RDBMSFunctionalDependency.FUNCTIONALDEPENDENCY)){
        		AssertionController<RDBMSFunctionalDependency> con = (RDBMSFunctionalDependencyController) apic.getController(RDBMSFunctionalDependency.class);
        		con.addAssertion(new RDBMSFunctionalDependency(dscon.getCurrentDataSource().getSourceID(), idOfMapping1, idOfMapping2, (RDBMSSQLQuery)map1.getSourceQuery(), (RDBMSSQLQuery)map2.getSourceQuery(), termOfM1, termOfM2));
        	}else if (assertion.equals(RDBMSInclusionDependency.INCLUSIONDEPENDENCY)){
        		AssertionController<RDBMSInclusionDependency> con = (RDBMSInclusionDependencyController) apic.getController(RDBMSInclusionDependency.class);
        		con.addAssertion(new RDBMSInclusionDependency(dscon.getCurrentDataSource().getSourceID(), idOfMapping1, idOfMapping2, (RDBMSSQLQuery)map1.getSourceQuery(), (RDBMSSQLQuery)map2.getSourceQuery(), termOfM1, termOfM2));
        	}else if (assertion.equals(RDBMSDisjointnessDependency.DISJOINEDNESSASSERTION)){
        		AssertionController<RDBMSDisjointnessDependency> con = (RDBMSDisjointnessDependencyController) apic.getController(RDBMSDisjointnessDependency.class);
        		con.addAssertion(new RDBMSDisjointnessDependency(dscon.getCurrentDataSource().getSourceID(), idOfMapping1, idOfMapping2, (RDBMSSQLQuery)map1.getSourceQuery(), (RDBMSSQLQuery)map2.getSourceQuery(), termOfM1, termOfM2));
        	}else{
        		throw new RuntimeException("Unknown assertion: " + assertion);
        	}
        	this.dispose();
        }

    }//GEN-LAST:event_jButtonCreateActionPerformed



//    /**
//    * @param args the command line arguments
//    */
//    public void showDialog() {
//        java.awt.EventQueue.invokeLater(new Runnable() {
//            public void run() {
//                CreateDependencyDialog dialog = new CreateDependencyDialog(new javax.swing.JFrame(), true);
//                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
//                    public void windowClosing(java.awt.event.WindowEvent e) {
//
//                    }
//                });
//                dialog.setVisible(true);
//            }
//        });
//    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonCancel;
    private javax.swing.JButton jButtonCreate;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabelMapping1;
    private javax.swing.JLabel jLabelMapping2;
    private javax.swing.JLabel jLabelMessage;
    private javax.swing.JLabel jLabelOutPut;
    private javax.swing.JLabel jLabelTitle;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTextField jTextFieldMap1;
    private javax.swing.JTextField jTextFieldMap2;
    // End of variables declaration//GEN-END:variables

}
