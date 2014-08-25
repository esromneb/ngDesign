/*******************************************************************************
 * Copyright (c) 2012-2013 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Nicolas Siret - initial API and implementation and/or initial documentation
 *    Matthieu Wipliez - refactoring and misc changes
 *******************************************************************************/
package com.synflow.core.internal.exporters

import com.synflow.core.IExportConfiguration.Target
import com.synflow.models.dpn.Actor
import com.synflow.models.dpn.DPN
import com.synflow.models.dpn.Instance
import com.synflow.models.dpn.Port
import com.synflow.models.ir.Var
import com.synflow.models.ir.util.IrUtil

import static com.synflow.core.IProperties.*
import static com.synflow.core.ISynflowConstants.*

/**
 * This class defines a generator of files useful in simulation.
 * 
 * @author Matthieu Wipliez
 * @author Nicolas Siret
 * 
 */
class SimFilesExporter extends Exporter {

	/**
	 * Exports simulation files.
	 */
	override doExport() {
		setParameters(FOLDER_SIM, Target.SIMULATION)

		val name = entity.simpleName
		writer.write(FOLDER_SIM + "/compile_" + name + ".tcl", printTcl())
		writer.write(FOLDER_SIM + "/wave.do", printWave())
	}

	def private printTcl() {
		'''
		puts {
		  ModelSimPE compile script
		  Generated by Synflow Studio for the «entity.simpleName» project
		}

		set PrefMain(font) {Courier 10 roman normal}

		set work work
		if {![file isdirectory $work]} {
		  vlib work
		  vmap work work
		}

		set library_file_list {
		  design_library {
		    «paths.join("\n")»
		  }
		  test_library {
		  	«'../' + generator.computePathTb(entity)»
		  }
		}

		# Compile out of date files
		set time_now [clock seconds]
		if [catch {set last_compile_time}] {
		  set last_compile_time 0
		}

		foreach {library file_list} $library_file_list {
		  foreach file $file_list {
		    if { $last_compile_time < [file mtime $file] } {
		      if [regexp {.vhd$} $file] {
		        vcom -2008 -reportprogress 30 -work work $file
		      } else {
		        vlog -reportprogress 300 -work work «FOR path: includePath»+incdir+«path» «ENDFOR»$file
		      }
		      set last_compile_time 0
		    }
		  }
		}
		set last_compile_time $time_now

		# Simulate
		vsim -novopt «entity.simpleName»_tb
		do wave.do
		run 10 us
		'''
	}

	def printWave() {
		val clocks = entity.properties.getAsJsonArray(PROP_CLOCKS)

		'''
		onerror {resume}
		quietly WaveActivateNextPane {} 0
		«FOR clock: clocks»
		add wave -noupdate -color White /«entity.simpleName»_tb/clock
		«ENDFOR»		
		add wave -noupdate -color White /«entity.simpleName»_tb/reset_n
		«IF entity instanceof DPN»
		«FOR instance : entity.instances»
			«printVertex('''/«entity.simpleName»_tb/«entity.simpleName»''', "", instance)»
		«ENDFOR»
		«ENDIF»
		TreeUpdate [SetDefaultTree]
		WaveRestoreCursors {{Cursor 1} {0 ps} 0}
		configure wave -namecolwidth 222
		configure wave -valuecolwidth 100
		configure wave -justifyvalue left
		configure wave -signalnamewidth 1
		configure wave -snapdistance 10
		configure wave -datasetprefix 0
		configure wave -rowmargin 4
		configure wave -childrowmargin 2
		configure wave -gridoffset 0
		configure wave -gridperiod 1
		configure wave -griddelta 40
		configure wave -timeline 0
		configure wave -timelineunits ns
		update
		WaveRestoreZoom {0 ps} {10 us}
		'''
	}

	def CharSequence printVertex(CharSequence parentHier, String group, Instance instance) {
		val entity = instance.entity
		val hier = '''«parentHier»/«instance.name»'''

		'''
		add wave -noupdate -expand«group» -divider <NULL>
		add wave -noupdate -expand«group» -group «instance.name» -divider <NULL>
		add wave -noupdate -expand«group» -group «instance.name» -divider «instance.name»
		add wave -noupdate -expand«group» -group «instance.name» -divider <NULL>
		«FOR port : entity.inputs»
			«printPort(group, hier, instance.name, port)»
		«ENDFOR»
		«FOR port : entity.outputs»
			«printPort(group, hier, instance.name, port)»
		«ENDFOR»
		«IF entity instanceof Actor»
			«FOR stateVar : entity.variables»
				«printStateVar(group, hier, instance.name, stateVar)»
			«ENDFOR»
			«IF entity.hasFsm»
				add wave -noupdate -expand«group» -group «instance.name» -expand -group StateVar -color {Magenta} «hier»/FSM
			«ENDIF»			
		«ENDIF»
		«IF entity instanceof DPN»
			«FOR inst : entity.instances»
				«printVertex('''«hier»''', group + " -group " + instance.name, inst)»
			«ENDFOR»
		«ENDIF»
		'''
	}

	def printPort(String group, CharSequence hier, String instName, Port port) {
		val name = '''«hier»/«port.name»'''
		val color = if(IrUtil.isInput(port)) "Cadet Blue" else "Orange Red"
		val dir = if(IrUtil.isInput(port)) "input" else "output"

		'''
		«IF port.sync»
		add wave -noupdate -expand«group» -group «instName» -expand -group «dir» -color {«color»} -radix hexadecimal «name»
		add wave -noupdate -expand«group» -group «instName» -expand -group «dir» -color {«color»} «name»_send
		«ELSE»
		add wave -noupdate -expand«group» -group «instName» -expand -group «dir» -color {Red} -radix hexadecimal «name»
		«ENDIF»
		'''
	}

	def printStateVar(String group, CharSequence hier, String instName, Var stateVar)
		'''
		«IF stateVar.assignable» 
		add wave -noupdate -expand«group» -group «instName» -expand -group StateVar -color {Magenta} -radix hexadecimal «hier»/«stateVar.name»
		«ENDIF»
		'''

}
