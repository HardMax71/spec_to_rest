service Preamble {

  predicate isValidURI(s: String) = s matches /^https?:..[^\s]+/

  predicate isValidEmail(s: String) = s matches /^[^@\s]+@[^@\s]+\.[^@\s]+$/

}
