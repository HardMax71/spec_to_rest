theory Smt_Fresh
  imports SpecRest_IR.IR
begin

text \<open>\<open>fresh_var base avoid\<close>: the first of \<open>base\<close>, \<open>base_\<close>, \<open>base__\<close>, ...
  not in \<open>avoid\<close>. The candidates have strictly increasing lengths, so among
  the first \<open>length avoid + 1\<close> of them one is always free (pigeonhole);
  \<open>fresh_var_fresh\<close> below turns that into an unconditional guarantee. This
  is what lets the translator introduce binders without reserving any
  identifier in the source language.\<close>

fun fresh_in :: "nat \<Rightarrow> String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal" where
  "fresh_in 0 base avoid = base"
| "fresh_in (Suc n) base avoid =
     (if string_in_list base avoid
        then fresh_in n (base + STR ''_'') avoid
        else base)"

definition fresh_var :: "String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal" where
  "fresh_var base avoid = fresh_in (Suc (length avoid)) base avoid"

lemma string_in_list_iff: "string_in_list y xs = (y \<in> set xs)"
  by (induction xs) auto

definition underscored :: "nat \<Rightarrow> String.literal \<Rightarrow> String.literal" where
  "underscored n base = ((\<lambda>s. s + STR ''_'') ^^ n) base"

lemma size_plus_literal:
  "size (s + t) = size s + size t" for s t :: String.literal
  including literal.lifting by transfer simp

lemma size_underscore_lit: "size (STR ''_'') = 1"
  including literal.lifting by transfer simp

lemma underscored_size:
  "size (underscored n base) = size base + n"
  by (induction n)
     (simp_all add: underscored_def size_plus_literal size_underscore_lit)

lemma underscored_inj: "inj (\<lambda>n. underscored n base)"
  by (rule injI) (metis underscored_size add_left_cancel)

lemma underscored_Suc_shift:
  "underscored (Suc n) base = underscored n (base + STR ''_'')"
  unfolding underscored_def by (induction n) simp_all

lemma fresh_in_finds:
  "\<exists>k<n. underscored k base \<notin> set avoid \<Longrightarrow> fresh_in n base avoid \<notin> set avoid"
proof (induction n arbitrary: base)
  case 0
  thus ?case by blast
next
  case (Suc n)
  show ?case
  proof (cases "string_in_list base avoid")
    case False
    thus ?thesis by (simp add: string_in_list_iff)
  next
    case True
    hence b_in: "base \<in> set avoid" by (simp add: string_in_list_iff)
    obtain k where kn: "k < Suc n" and kf: "underscored k base \<notin> set avoid"
      using Suc.prems by blast
    have "k \<noteq> 0" using kf b_in by (cases k) (auto simp: underscored_def)
    then obtain k' where keq: "k = Suc k'" by (cases k) auto
    have "underscored k' (base + STR ''_'') \<notin> set avoid"
      using kf keq underscored_Suc_shift by simp
    moreover have "k' < n" using kn keq by simp
    ultimately have "fresh_in n (base + STR ''_'') avoid \<notin> set avoid"
      using Suc.IH by blast
    thus ?thesis using True by simp
  qed
qed

lemma fresh_var_fresh:
  "fresh_var base avoid \<notin> set avoid"
proof -
  have inj: "inj_on (\<lambda>k. underscored k base) {..length avoid}"
    using underscored_inj inj_on_subset by blast
  have "\<not> (\<lambda>k. underscored k base) ` {..length avoid} \<subseteq> set avoid"
  proof
    assume "(\<lambda>k. underscored k base) ` {..length avoid} \<subseteq> set avoid"
    hence "card ((\<lambda>k. underscored k base) ` {..length avoid}) \<le> card (set avoid)"
      by (intro card_mono) auto
    hence "Suc (length avoid) \<le> card (set avoid)"
      by (simp add: card_image inj)
    thus False using card_length[of avoid] by simp
  qed
  then obtain k where "k \<le> length avoid" and "underscored k base \<notin> set avoid"
    by auto
  hence "\<exists>k<Suc (length avoid). underscored k base \<notin> set avoid"
    using le_imp_less_Suc by blast
  thus ?thesis unfolding fresh_var_def by (rule fresh_in_finds)
qed

end
