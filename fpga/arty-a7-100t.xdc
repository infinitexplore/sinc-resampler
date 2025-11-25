## Clock signal
set_property -dict { PACKAGE_PIN E3    IOSTANDARD LVCMOS33 } [get_ports { clock }]; #IO_L12P_T1_MRCC_35 Sch=gnd
create_clock -add -name sys_clk_pin -period 10.00 -waveform {0 5} [get_ports { clock }];

# Reset
set_property -dict { PACKAGE_PIN C2 IOSTANDARD LVCMOS33 } [get_ports { reset }]

##USB-UART Interface
set_property -dict { PACKAGE_PIN D10   IOSTANDARD LVCMOS33 } [get_ports { io_txd }];

## LEDs
set_property -dict { PACKAGE_PIN H5    IOSTANDARD LVCMOS33 } [get_ports { io_finished }]; #IO_L24N_T3_35 Sch=led[4]
set_property -dict { PACKAGE_PIN J5    IOSTANDARD LVCMOS33 } [get_ports { io_newSampleFire }]; #IO_25_35 Sch=led[5]

## Configuration Bank Voltage Select
set_property CFGBVS VCCO [current_design]
set_property CONFIG_VOLTAGE 3.3 [current_design]
