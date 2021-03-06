---------------------------------------------------------------------------------
-- Copyright (c) 2012-2013 Synflow SAS.
-- All rights reserved. This program and the accompanying materials
-- are made available under the terms of the Eclipse Public License v1.0
-- which accompanies this distribution, and is available at
-- http://www.eclipse.org/legal/epl-v10.html
--
-- Contributors:
--    Nicolas Siret - initial API and implementation and/or initial documentation
--    Matthieu Wipliez - refactoring and maintenance
---------------------------------------------------------------------------------

-------------------------------------------------------------------------------
-- Title      : FIFO write controller
-- Author     : Nicolas Siret (nicolas.siret@synflow.com)
--              Matthieu Wipliez (matthieu.wipliez@synflow.com)
-- Standard   : VHDL'93
-------------------------------------------------------------------------------


library ieee;
use ieee.std_logic_1164.all;
use ieee.std_logic_unsigned.all;
use ieee.numeric_std.all;

-------------------------------------------------------------------------------

entity FIFO_Write_Controller is
  generic (
    depth : integer := 8);
  port (
    reset_n     : in  std_logic;
    wr_clock    : in  std_logic;
    --
    enable      : in  std_logic;
    rd_address  : in  unsigned(depth - 1 downto 0);
    --
    full        : out std_logic;
    almost_full : out std_logic;
    wr_address  : out unsigned(depth - 1 downto 0)
    );
end FIFO_Write_Controller;

-------------------------------------------------------------------------------

architecture arch_FIFO_Write_Controller of FIFO_Write_Controller is

  -----------------------------------------------------------------------------
  -- Constants and signals declaration
  -----------------------------------------------------------------------------
  constant unsigned_depth_p : unsigned (depth - 1 downto 0) := to_unsigned((2** depth + 2) - (2** depth)/4, depth);
  constant unsigned_depth_n : unsigned (depth - 1 downto 0) := to_unsigned((2** depth + 2) - ((2** depth)*3)/4, depth);

  signal next_wr_address     : unsigned(depth - 1 downto 0);
  signal i_wr_address        : unsigned(depth - 1 downto 0);
  --
  signal always_next         : unsigned(depth - 1 downto 0);
  signal almost_wr_address_p : unsigned(depth - 1 downto 0);
  signal almost_wr_address_n : unsigned(depth - 1 downto 0);
  -------------------------------------------------------------------------------

begin
  wr_address <= i_wr_address;

  -- Synchro  
  counter_sync : process (reset_n, wr_clock) is
  begin
    if reset_n = '0' then
      i_wr_address <= (others => '0');
    elsif rising_edge(wr_clock) then
      i_wr_address <= next_wr_address;
    end if;
  end process counter_sync;

  -- Incremental counter
  counter_proc : process (enable, i_wr_address) is
  begin
    next_wr_address     <= i_wr_address + unsigned'("" & enable);
    -- Flags
    always_next         <= i_wr_address + "1";
    almost_wr_address_p <= i_wr_address + unsigned_depth_p;
    almost_wr_address_n <= i_wr_address + unsigned_depth_n;
  end process counter_proc;

  -- Flag management
  fullFlag : process (always_next, rd_address) is
  begin
    if always_next = rd_address then
      full <= '1';
    else
      full <= '0';
    end if;
  end process fullFlag;

  AlmostfullFlag : process (reset_n, wr_clock) is
  begin
    if reset_n = '0' then
      almost_full <= '0';
    elsif rising_edge(wr_clock) then
      if almost_wr_address_n = rd_address then
        almost_full <= '1';
      elsif almost_wr_address_p = rd_address then
        almost_full <= '0';
      end if;
    end if;
  end process AlmostfullFlag;

end arch_FIFO_Write_Controller;