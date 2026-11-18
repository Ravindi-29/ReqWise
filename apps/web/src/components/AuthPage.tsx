import React, { useState, useEffect } from 'react';
import { Sparkles, Eye, EyeOff, ArrowRight, ArrowLeft, ShieldCheck, User, Lock, Mail, Globe, X, AlertCircle, CheckCircle2 } from 'lucide-react';
import { AuthUser } from '../types';

interface AuthPageProps {
  onAuthenticated: (user: AuthUser) => void;
}

interface RegisteredAccount extends AuthUser {
  password?: string;
}

const getRegisteredUsers = (): RegisteredAccount[] => {
  try {
    return JSON.parse(localStorage.getItem('reqwise_registered_users') || '[]');
  } catch {
    return [];
  }
};

export const AuthPage: React.FC<AuthPageProps> = ({ onAuthenticated }) => {
  // Load saved state from localStorage (preserving what user did before)
  const [view, setView] = useState<'signin' | 'signup'>(() => {
    return (localStorage.getItem('reqwise_saved_view') as 'signin' | 'signup') || 'signin';
  });

  // Sign In Form State with preserved saved values
  const [signinUsername, setSigninUsername] = useState(() => {
    return localStorage.getItem('reqwise_saved_username') || '';
  });
  const [signinPassword, setSigninPassword] = useState('');
  const [showSigninPassword, setShowSigninPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(() => {
    return localStorage.getItem('reqwise_remember_flag') === 'true';
  });
  const [signinError, setSigninError] = useState('');

  // Sign Up Form State with preserved saved values
  const [signupUsername, setSignupUsername] = useState(() => {
    return localStorage.getItem('reqwise_draft_username') || '';
  });
  const [signupPassword, setSignupPassword] = useState('');
  const [showSignupPassword, setShowSignupPassword] = useState(false);
  const [signupEmail, setSignupEmail] = useState(() => {
    return localStorage.getItem('reqwise_draft_email') || '';
  });
  const [signupFirstName, setSignupFirstName] = useState(() => {
    return localStorage.getItem('reqwise_draft_firstname') || '';
  });
  const [signupLastName, setSignupLastName] = useState(() => {
    return localStorage.getItem('reqwise_draft_lastname') || '';
  });
  const [signupCountry, setSignupCountry] = useState(() => {
    return localStorage.getItem('reqwise_draft_country') || 'Sri Lanka';
  });

  // SSO Interactive Modal State
  const [ssoModal, setSsoModal] = useState<'google' | 'github' | null>(null);
  const [ssoAccountInput, setSsoAccountInput] = useState('');
  const [ssoPasswordInput, setSsoPasswordInput] = useState('');
  const [ssoError, setSsoError] = useState('');

  // Sync draft inputs to localStorage so what the user did before is always saved
  useEffect(() => {
    if (signinUsername) {
      localStorage.setItem('reqwise_saved_username', signinUsername);
    }
  }, [signinUsername]);

  useEffect(() => {
    localStorage.setItem('reqwise_remember_flag', rememberMe ? 'true' : 'false');
  }, [rememberMe]);

  useEffect(() => {
    localStorage.setItem('reqwise_saved_view', view);
  }, [view]);

  useEffect(() => {
    if (signupUsername) localStorage.setItem('reqwise_draft_username', signupUsername);
    if (signupEmail) localStorage.setItem('reqwise_draft_email', signupEmail);
    if (signupFirstName) localStorage.setItem('reqwise_draft_firstname', signupFirstName);
    if (signupLastName) localStorage.setItem('reqwise_draft_lastname', signupLastName);
    if (signupCountry) localStorage.setItem('reqwise_draft_country', signupCountry);
  }, [signupUsername, signupEmail, signupFirstName, signupLastName, signupCountry]);

  // Password Validation Rules for Sign Up
  const passwordRules = {
    length: signupPassword.length >= 8,
    case: /[a-z]/.test(signupPassword) && /[A-Z]/.test(signupPassword),
    digit: /\d/.test(signupPassword),
    special: /[*#\-_@!$%^&+=?]/.test(signupPassword),
  };

  const handleSignIn = (e: React.FormEvent) => {
    e.preventDefault();
    setSigninError('');

    const cleanUsername = signinUsername.trim();
    if (!cleanUsername) {
      setSigninError('Please enter your username or email.');
      return;
    }
    if (!signinPassword) {
      setSigninError('Please enter your password.');
      return;
    }

    const query = cleanUsername.toLowerCase();
    const registered = getRegisteredUsers();

    // Verify credentials against registered accounts
    const matchedAccount = registered.find(
      (u) =>
        (u.username && u.username.toLowerCase() === query) ||
        (u.email && u.email.toLowerCase() === query)
    );

    if (matchedAccount) {
      // Validate password strictly
      if (matchedAccount.password && matchedAccount.password !== signinPassword) {
        setSigninError('Incorrect password. Please verify your password and try again.');
        return; // Reject wrong password, DO NOT log in!
      }
    } else {
      // If not registered yet, display error
      setSigninError('No account found with this username or email. Please click "Create new account" below.');
      return; // Reject un-registered user, DO NOT log in!
    }

    // Save what user did before
    localStorage.setItem('reqwise_saved_username', cleanUsername);
    if (rememberMe) {
      localStorage.setItem('reqwise_remember_username', cleanUsername);
      localStorage.setItem('reqwise_remember_flag', 'true');
    } else {
      localStorage.removeItem('reqwise_remember_username');
      localStorage.setItem('reqwise_remember_flag', 'false');
    }

    const user: AuthUser = {
      username: matchedAccount.username || cleanUsername,
      email: matchedAccount.email || (cleanUsername.includes('@') ? cleanUsername : `${cleanUsername}@reqwise.internal`),
      firstName: matchedAccount.firstName || cleanUsername,
      lastName: matchedAccount.lastName,
      country: matchedAccount.country,
    };

    localStorage.setItem('reqwise_saved_auth', JSON.stringify(user));
    onAuthenticated(user);
  };

  const handleSignUp = (e: React.FormEvent) => {
    e.preventDefault();
    setSigninError('');

    if (!signupPassword) {
      return;
    }

    const user: RegisteredAccount = {
      username: signupUsername,
      email: signupEmail,
      password: signupPassword,
      firstName: signupFirstName,
      lastName: signupLastName,
      country: signupCountry,
    };

    // Save registered user account in localStorage with password
    try {
      const existing = getRegisteredUsers();
      const filtered = existing.filter(
        (u) =>
          u.email.toLowerCase() !== signupEmail.toLowerCase() &&
          u.username.toLowerCase() !== signupUsername.toLowerCase()
      );
      filtered.push(user);
      localStorage.setItem('reqwise_registered_users', JSON.stringify(filtered));
    } catch {
      localStorage.setItem('reqwise_registered_users', JSON.stringify([user]));
    }

    // Save for Sign In prefill
    localStorage.setItem('reqwise_saved_username', signupEmail);
    localStorage.setItem('reqwise_saved_auth', JSON.stringify(user));
    setSigninUsername(signupEmail);

    // Transition to workspace
    onAuthenticated(user);
  };

  const openSSOModal = (provider: 'google' | 'github') => {
    setSsoError('');
    setSsoPasswordInput('');
    if (provider === 'google') {
      const savedGoogle = localStorage.getItem('reqwise_saved_google_email') || '';
      setSsoAccountInput(savedGoogle);
    } else {
      const savedGithub = localStorage.getItem('reqwise_saved_github_user') || '';
      setSsoAccountInput(savedGithub);
    }
    setSsoModal(provider);
  };

  const handleSSOSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSsoError('');

    const trimmed = ssoAccountInput.trim();
    if (!trimmed) {
      setSsoError(
        ssoModal === 'google'
          ? 'Please enter your Google account email.'
          : 'Please enter your GitHub username or work email.'
      );
      return;
    }

    if (ssoModal === 'google' && !trimmed.includes('@')) {
      setSsoError('Please enter a valid Google email address (e.g. sarah@gmail.com).');
      return;
    }

    if (!ssoPasswordInput || !ssoPasswordInput.trim()) {
      setSsoError(
        ssoModal === 'google'
          ? 'Please enter your Google account password.'
          : 'Please enter your GitHub password or Personal Access Token.'
      );
      return;
    }

    const query = trimmed.toLowerCase();
    const registered = getRegisteredUsers();
    const matchedAccount = registered.find(
      (u) =>
        (u.username && u.username.toLowerCase() === query) ||
        (u.email && u.email.toLowerCase() === query)
    );

    // If account was already registered, strictly verify password
    if (matchedAccount && matchedAccount.password) {
      if (matchedAccount.password !== ssoPasswordInput) {
        setSsoError(
          ssoModal === 'google'
            ? 'Authentication failed: Incorrect Google password.'
            : 'Authentication failed: Incorrect GitHub password or Personal Access Token.'
        );
        return; // Reject wrong password, DO NOT log in!
      }
    } else {
      // If registering new SSO account, ensure password is at least 8 characters
      if (ssoPasswordInput.length < 8) {
        setSsoError(
          ssoModal === 'google'
            ? 'Authentication failed: Password must be at least 8 characters.'
            : 'Authentication failed: Password or Personal Access Token must be at least 8 characters.'
        );
        return;
      }

      // Save new SSO account into registered users
      const isEmail = trimmed.includes('@');
      const username = isEmail ? trimmed.split('@')[0] : trimmed;
      const email = isEmail ? trimmed : `${trimmed}@github.com`;
      const newSSOAccount: RegisteredAccount = {
        username,
        email,
        password: ssoPasswordInput,
        firstName: username.charAt(0).toUpperCase() + username.slice(1),
        lastName: ssoModal === 'google' ? 'Google' : 'GitHub',
        country: 'Sri Lanka',
      };
      localStorage.setItem('reqwise_registered_users', JSON.stringify([...registered, newSSOAccount]));
    }

    const isEmail = trimmed.includes('@');
    const username = matchedAccount?.username || (isEmail ? trimmed.split('@')[0] : trimmed);
    const email = matchedAccount?.email || (isEmail ? trimmed : `${trimmed}@github.com`);

    const user: AuthUser = {
      username: username,
      email: email,
      firstName: matchedAccount?.firstName || username.charAt(0).toUpperCase() + username.slice(1),
      lastName: matchedAccount?.lastName || (ssoModal === 'google' ? 'Google' : 'GitHub'),
    };

    if (ssoModal === 'google') {
      localStorage.setItem('reqwise_saved_google_email', email);
    } else {
      localStorage.setItem('reqwise_saved_github_user', trimmed);
    }
    localStorage.setItem('reqwise_saved_username', trimmed);
    localStorage.setItem('reqwise_saved_auth', JSON.stringify(user));

    setSsoModal(null);
    onAuthenticated(user);
  };

  return (
    <div className="min-h-screen bg-[#020617] text-slate-100 flex flex-col font-sans relative overflow-x-hidden">
      {/* Background radial glows matching ReqWise deep space aesthetic */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[500px] bg-sky-500/10 rounded-full blur-[130px]" />
        <div className="absolute bottom-10 right-10 w-[450px] h-[350px] bg-indigo-600/10 rounded-full blur-[110px]" />
      </div>

      {/* Top Navbar */}
      <header className="h-16 border-b border-slate-800 bg-slate-900/90 backdrop-blur px-6 flex items-center justify-between sticky top-0 z-50">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-sky-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-sky-500/20">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-bold text-lg tracking-tight text-white">ReqWise</span>
            </div>
            <p className="text-xs text-slate-400 font-mono">SRS Intelligence Engine</p>
          </div>
        </div>

        {/* View Toggle Tabs */}
        <div className="flex items-center bg-slate-950/80 p-1 rounded-xl border border-slate-800 text-xs">
          <button
            type="button"
            onClick={() => {
              setView('signin');
              setSigninError('');
              localStorage.setItem('reqwise_saved_view', 'signin');
            }}
            className={`px-3.5 py-1.5 rounded-lg font-medium transition-all ${
              view === 'signin'
                ? 'bg-sky-600 text-white shadow-sm shadow-sky-600/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => {
              setView('signup');
              setSigninError('');
              localStorage.setItem('reqwise_saved_view', 'signup');
            }}
            className={`px-3.5 py-1.5 rounded-lg font-medium transition-all ${
              view === 'signup'
                ? 'bg-sky-600 text-white shadow-sm shadow-sky-600/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Sign Up
          </button>
        </div>
      </header>

      {/* Main Content Container */}
      <main className="flex-1 flex items-center justify-center p-4 sm:p-6 md:p-10 relative z-10">
        {view === 'signin' ? (
          /* ======================================================== */
          /* SIGN IN PAGE (Split-panel Layout from Reference Image 1)  */
          /* ======================================================== */
          <div className="w-full max-w-4xl bg-slate-900/90 backdrop-blur-xl rounded-2xl shadow-2xl border border-slate-800 overflow-hidden transition-all duration-300">
            <div className="grid grid-cols-1 md:grid-cols-12 min-h-[500px]">
              
              {/* Left Column: Credentials Login */}
              <div className="md:col-span-7 p-6 sm:p-8 lg:p-10 flex flex-col justify-between">
                <div>
                  {/* ReqWise Branding Header (without ISO badge) */}
                  <div className="flex items-center space-x-3.5 mb-8">
                    <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-sky-500 to-indigo-600 flex items-center justify-center shadow-xl shadow-sky-500/25">
                      <Sparkles className="w-6 h-6 text-white" />
                    </div>
                    <div>
                      <h2 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight">
                        ReqWise
                      </h2>
                      <p className="text-xs text-slate-400 font-mono">
                        Automated SRS Analysis &amp; Intelligence Platform
                      </p>
                    </div>
                  </div>

                  {/* Prominent Error Alert */}
                  {signinError && (
                    <div className="mb-4 p-3.5 rounded-xl bg-red-950/60 border border-red-800/80 text-xs text-red-300 flex items-center space-x-2.5 animate-in fade-in duration-200">
                      <AlertCircle className="w-4 h-4 text-red-400 flex-shrink-0" />
                      <span className="font-medium">{signinError}</span>
                    </div>
                  )}

                  {/* Sign In Form */}
                  <form onSubmit={handleSignIn} className="space-y-4">
                    {/* Username or Email */}
                    <div>
                      <label className="sr-only">Username or Email</label>
                      <div className="relative">
                        <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                          <User className="w-4 h-4" />
                        </div>
                        <input
                          type="text"
                          required
                          value={signinUsername}
                          onChange={(e) => {
                            setSigninUsername(e.target.value);
                            setSigninError('');
                          }}
                          placeholder="Username or email"
                          className="w-full pl-10 pr-4 py-3 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all"
                        />
                      </div>
                    </div>

                    {/* Password with Error Highlight */}
                    <div>
                      <label className="sr-only">Password</label>
                      <div className="relative">
                        <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                          <Lock className="w-4 h-4" />
                        </div>
                        <input
                          type={showSigninPassword ? 'text' : 'password'}
                          required
                          value={signinPassword}
                          onChange={(e) => {
                            setSigninPassword(e.target.value);
                            setSigninError('');
                          }}
                          placeholder="Password"
                          className={`w-full pl-10 pr-10 py-3 rounded-xl bg-slate-950/80 border ${
                            signinError.toLowerCase().includes('password')
                              ? 'border-red-500 focus:border-red-500 focus:ring-2 focus:ring-red-500/20'
                              : 'border-slate-800 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20'
                          } text-slate-100 placeholder-slate-500 focus:outline-none text-sm transition-all`}
                        />
                        <button
                          type="button"
                          onClick={() => setShowSigninPassword(!showSigninPassword)}
                          className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-200 focus:outline-none"
                          title="Toggle password visibility"
                        >
                          {showSigninPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>

                    {/* Remember Me & Forgot Password */}
                    <div className="flex items-center justify-between text-xs pt-1">
                      <label className="flex items-center text-slate-400 hover:text-slate-300 cursor-pointer select-none">
                        <input
                          type="checkbox"
                          checked={rememberMe}
                          onChange={(e) => setRememberMe(e.target.checked)}
                          className="w-3.5 h-3.5 rounded bg-slate-950 border-slate-700 text-sky-500 focus:ring-sky-500/30"
                        />
                        <span className="ml-2">Remember username</span>
                      </label>
                      <a
                        href="#forgot-password"
                        onClick={(e) => {
                          e.preventDefault();
                          alert('Password reset instructions will be sent to your registered email.');
                        }}
                        className="text-sky-400 hover:text-sky-300 transition-colors"
                      >
                        Lost password?
                      </a>
                    </div>

                    {/* Primary Button named exactly "Sign In" */}
                    <button
                      type="submit"
                      className="w-full flex items-center justify-center space-x-2 py-3 px-4 rounded-xl text-sm font-semibold bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white shadow-xl shadow-sky-500/25 transition-all transform hover:-translate-y-0.5 cursor-pointer mt-4"
                    >
                      <span>Sign In</span>
                      <ArrowRight className="w-4 h-4 ml-1" />
                    </button>
                  </form>
                </div>

                {/* Bottom: Transition to Sign Up */}
                <div className="mt-8 pt-5 border-t border-slate-800/80 text-center text-xs text-slate-400">
                  <span>Don't have an account?</span>
                  <button
                    type="button"
                    onClick={() => {
                      setView('signup');
                      setSigninError('');
                      localStorage.setItem('reqwise_saved_view', 'signup');
                    }}
                    className="ml-1.5 text-sky-400 hover:text-sky-300 font-semibold cursor-pointer underline underline-offset-4 focus:outline-none"
                  >
                    Create new account
                  </button>
                </div>
              </div>

              {/* Right Column: Third-Party SSO (Opens account input modal, strictly requires credentials) */}
              <div className="md:col-span-5 bg-slate-950/60 p-6 sm:p-8 lg:p-10 flex flex-col justify-between border-t md:border-t-0 md:border-l border-slate-800">
                <div>
                  <h3 className="text-xs font-semibold text-slate-300 uppercase tracking-wider mb-5 text-center md:text-left">
                    Sign in with
                  </h3>

                  <div className="space-y-3">
                    {/* Google Account Button */}
                    <button
                      type="button"
                      onClick={() => openSSOModal('google')}
                      className="w-full flex items-center justify-center space-x-3 py-2.5 px-4 bg-slate-900 hover:bg-slate-800 border border-slate-700/80 rounded-xl text-xs font-medium text-slate-200 shadow-sm transition duration-150 cursor-pointer"
                    >
                      <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 24 24">
                        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                      </svg>
                      <span>Google Account</span>
                    </button>

                    {/* GitHub Enterprise Button */}
                    <button
                      type="button"
                      onClick={() => openSSOModal('github')}
                      className="w-full flex items-center justify-center space-x-3 py-2.5 px-4 bg-slate-900 hover:bg-slate-800 border border-slate-700/80 rounded-xl text-xs font-medium text-slate-200 shadow-sm transition duration-150 cursor-pointer"
                    >
                      <svg className="w-4 h-4 fill-current text-white flex-shrink-0" viewBox="0 0 24 24">
                        <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/>
                      </svg>
                      <span>GitHub Enterprise</span>
                    </button>
                  </div>

                  {/* Advisory Notice */}
                  <p className="text-[12px] text-slate-400 mt-6 leading-relaxed">
                    If you are unable to use Google Account or GitHub login methods you can self-register by creating a manual{' '}
                    <span className="font-mono text-sky-400">reqwise</span> account{' '}
                    <button
                      type="button"
                      onClick={() => {
                        setView('signup');
                        setSigninError('');
                        localStorage.setItem('reqwise_saved_view', 'signup');
                      }}
                      className="text-sky-400 hover:text-sky-300 underline font-medium cursor-pointer inline p-0 m-0 border-0 bg-transparent"
                    >
                      from here
                    </button>.
                  </p>
                </div>

                {/* Compliance & Security Badge */}
                <div className="mt-8 pt-4 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-500">
                  <span className="flex items-center space-x-1.5">
                    <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Enterprise Grade Security</span>
                  </span>
                  <a
                    href="#security-notice"
                    onClick={(e) => {
                      e.preventDefault();
                      alert('ReqWise Security Policy: Zero data retention on audit models; AES-256 encryption at rest.');
                    }}
                    className="text-slate-400 hover:text-slate-300 underline"
                  >
                    Security Notice
                  </a>
                </div>
              </div>

            </div>
          </div>
        ) : (
          /* ======================================================== */
          /* SIGN UP PAGE (From Reference Image 2 Idea)               */
          /* Excluded: City/town, Security question, Email (again)    */
          /* ======================================================== */
          <div className="w-full max-w-xl bg-slate-900/90 backdrop-blur-xl rounded-2xl shadow-2xl border border-slate-800 overflow-hidden transition-all duration-300">
            <div className="p-6 sm:p-8 md:p-10">
              
              {/* Header */}
              <div className="flex items-center justify-between pb-4 border-b border-slate-800 mb-6">
                <div className="flex items-center space-x-3">
                  <button
                    type="button"
                    onClick={() => {
                      setView('signin');
                      setSigninError('');
                      localStorage.setItem('reqwise_saved_view', 'signin');
                    }}
                    title="Back to Sign In"
                    className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                  >
                    <ArrowLeft className="w-5 h-5" />
                  </button>
                  <div>
                    <h2 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
                      New account
                    </h2>
                  </div>
                </div>
                <span className="text-xs text-slate-400 font-medium flex items-center">
                  <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/40 text-[9px] mr-1.5 font-bold">i</span>
                  Required
                </span>
              </div>

              {/* Registration Form */}
              <form onSubmit={handleSignUp} className="space-y-4">
                
                {/* Field 1: Username */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="block text-xs font-semibold text-slate-300">
                      Username
                    </label>
                    <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/40 text-[9px] font-bold" title="Required field">i</span>
                  </div>
                  <input
                    type="text"
                    required
                    value={signupUsername}
                    onChange={(e) => setSignupUsername(e.target.value)}
                    placeholder="Username"
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all"
                  />
                </div>

                {/* Password Policy Helper Box */}
                <div className="p-3 bg-slate-950/70 rounded-xl border border-slate-800 text-[11px] text-slate-400 leading-relaxed space-y-1">
                  <p>The password must have at least 8 characters, at least 1 digit(s), at least 1 lower case letter(s), at least 1 upper case letter(s), at least 1 special character(s) such as *, -, or #</p>
                </div>

                {/* Field 2: Password */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="block text-xs font-semibold text-slate-300">
                      Password
                    </label>
                    <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/40 text-[9px] font-bold" title="Required field">i</span>
                  </div>
                  <div className="relative">
                    <input
                      type={showSignupPassword ? 'text' : 'password'}
                      required
                      value={signupPassword}
                      onChange={(e) => setSignupPassword(e.target.value)}
                      placeholder="Password"
                      className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all pr-10"
                    />
                    <button
                      type="button"
                      onClick={() => setShowSignupPassword(!showSignupPassword)}
                      className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-200 focus:outline-none"
                    >
                      {showSignupPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                  {/* Real-time Password Rules Pills */}
                  {signupPassword && (
                    <div className="grid grid-cols-4 gap-1.5 mt-2 text-[10px] font-mono">
                      <span className={`px-1.5 py-0.5 rounded text-center border ${passwordRules.length ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40' : 'bg-slate-800 text-slate-400 border-slate-700'}`}>
                        8+ chars
                      </span>
                      <span className={`px-1.5 py-0.5 rounded text-center border ${passwordRules.case ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40' : 'bg-slate-800 text-slate-400 border-slate-700'}`}>
                        Upper &amp; Lower
                      </span>
                      <span className={`px-1.5 py-0.5 rounded text-center border ${passwordRules.digit ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40' : 'bg-slate-800 text-slate-400 border-slate-700'}`}>
                        1+ Digit
                      </span>
                      <span className={`px-1.5 py-0.5 rounded text-center border ${passwordRules.special ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40' : 'bg-slate-800 text-slate-400 border-slate-700'}`}>
                        Special (* - #)
                      </span>
                    </div>
                  )}
                </div>

                {/* Field 3: Email address (Single Email Field with placeholder sarah@gmail.com) */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="block text-xs font-semibold text-slate-300">
                      Email address
                    </label>
                    <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/40 text-[9px] font-bold" title="Required field">i</span>
                  </div>
                  <input
                    type="email"
                    required
                    value={signupEmail}
                    onChange={(e) => setSignupEmail(e.target.value)}
                    placeholder="sarah@gmail.com"
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all"
                  />
                </div>

                {/* Fields 4 & 5: First name & Last name */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <label className="block text-xs font-semibold text-slate-300">
                        First name
                      </label>
                      <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/40 text-[9px] font-bold" title="Required field">i</span>
                    </div>
                    <input
                      type="text"
                      required
                      value={signupFirstName}
                      onChange={(e) => setSignupFirstName(e.target.value)}
                      placeholder="First name"
                      className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all"
                    />
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <label className="block text-xs font-semibold text-slate-300">
                        Last name
                      </label>
                      <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/40 text-[9px] font-bold" title="Required field">i</span>
                    </div>
                    <input
                      type="text"
                      required
                      value={signupLastName}
                      onChange={(e) => setSignupLastName(e.target.value)}
                      placeholder="Last name"
                      className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all"
                    />
                  </div>
                </div>

                {/* Field 6: Country dropdown (City/town & Security question excluded) */}
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                    Country
                  </label>
                  <select
                    value={signupCountry}
                    onChange={(e) => setSignupCountry(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-200 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all"
                  >
                    <option value="Sri Lanka">Sri Lanka</option>
                    <option value="United States">United States</option>
                    <option value="United Kingdom">United Kingdom</option>
                    <option value="Australia">Australia</option>
                    <option value="Canada">Canada</option>
                    <option value="Germany">Germany</option>
                    <option value="India">India</option>
                    <option value="Japan">Japan</option>
                    <option value="Singapore">Singapore</option>
                    <option value="Other">Other Global Region</option>
                  </select>
                </div>

                {/* Action Buttons: Sign Up & Cancel */}
                <div className="pt-4 flex items-center space-x-3">
                  <button
                    type="submit"
                    className="py-2.5 px-6 rounded-xl bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white font-semibold text-xs tracking-wide shadow-lg shadow-sky-500/20 transition duration-150 cursor-pointer"
                  >
                    Sign Up
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      setView('signin');
                      setSigninError('');
                      localStorage.setItem('reqwise_saved_view', 'signin');
                    }}
                    className="py-2.5 px-5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium text-xs transition duration-150 cursor-pointer border border-slate-700"
                  >
                    Cancel
                  </button>
                </div>
              </form>

              {/* Back to login footer note */}
              <div className="mt-6 pt-4 border-t border-slate-800/80 text-center text-xs text-slate-400">
                Already have an account?
                <button
                  type="button"
                  onClick={() => {
                    setView('signin');
                    setSigninError('');
                    localStorage.setItem('reqwise_saved_view', 'signin');
                  }}
                  className="text-sky-400 hover:text-sky-300 font-semibold underline underline-offset-4 cursor-pointer ml-1"
                >
                  Sign In
                </button>
              </div>

            </div>
          </div>
        )}
      </main>

      {/* ======================================================== */}
      {/* INTERACTIVE SSO ACCOUNT AUTHENTICATION MODAL              */}
      {/* Strictly validates password and credentials               */}
      {/* ======================================================== */}
      {ssoModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl p-6 sm:p-8 relative">
            <button
              onClick={() => {
                setSsoModal(null);
                setSsoError('');
              }}
              className="absolute top-4 right-4 p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
              title="Close"
            >
              <X className="w-5 h-5" />
            </button>

            {/* Modal Brand Header */}
            <div className="flex items-center space-x-3 mb-6">
              {ssoModal === 'google' ? (
                <div className="w-10 h-10 rounded-xl bg-white flex items-center justify-center shadow-md">
                  <svg className="w-5 h-5" viewBox="0 0 24 24">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                  </svg>
                </div>
              ) : (
                <div className="w-10 h-10 rounded-xl bg-slate-800 flex items-center justify-center border border-slate-700 shadow-md">
                  <svg className="w-5 h-5 fill-current text-white" viewBox="0 0 24 24">
                    <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/>
                  </svg>
                </div>
              )}
              <div>
                <h3 className="text-base font-bold text-white">
                  {ssoModal === 'google' ? 'Sign in with Google' : 'Sign in with GitHub Enterprise'}
                </h3>
                <p className="text-xs text-slate-400">
                  {ssoModal === 'google'
                    ? 'Enter your Google account credentials to continue'
                    : 'Enter your GitHub username or organization email'}
                </p>
              </div>
            </div>

            {/* Error Message */}
            {ssoError && (
              <div className="mb-4 p-3.5 rounded-xl bg-red-950/60 border border-red-800/80 text-xs text-red-300 flex items-center space-x-2.5 animate-in fade-in duration-200">
                <AlertCircle className="w-4 h-4 text-red-400 flex-shrink-0" />
                <span className="font-medium">{ssoError}</span>
              </div>
            )}

            {/* SSO Credential Form */}
            <form onSubmit={handleSSOSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                  {ssoModal === 'google' ? 'Google Email address' : 'GitHub Username or Email'}
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Mail className="w-4 h-4" />
                  </div>
                  <input
                    type={ssoModal === 'google' ? 'email' : 'text'}
                    required
                    autoFocus
                    value={ssoAccountInput}
                    onChange={(e) => {
                      setSsoAccountInput(e.target.value);
                      setSsoError('');
                    }}
                    placeholder={ssoModal === 'google' ? 'sarah@gmail.com' : 'sarah-chen or sarah@enterprise.io'}
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20 text-sm transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                  {ssoModal === 'google' ? 'Google Password' : 'Password or Personal Access Token'}
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Lock className="w-4 h-4" />
                  </div>
                  <input
                    type="password"
                    required
                    value={ssoPasswordInput}
                    onChange={(e) => {
                      setSsoPasswordInput(e.target.value);
                      setSsoError('');
                    }}
                    placeholder={ssoModal === 'google' ? 'Enter Google password' : 'Enter password or access token'}
                    className={`w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-950 border ${
                      ssoError.toLowerCase().includes('password') || ssoError.toLowerCase().includes('token')
                        ? 'border-red-500 focus:border-red-500 focus:ring-2 focus:ring-red-500/20'
                        : 'border-slate-800 focus:border-sky-500 focus:ring-2 focus:ring-sky-500/20'
                    } text-slate-100 placeholder-slate-500 focus:outline-none text-sm transition-all`}
                  />
                </div>
              </div>

              <div className="pt-2 flex items-center space-x-3">
                <button
                  type="submit"
                  className="flex-1 py-2.5 px-4 rounded-xl bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white font-semibold text-xs tracking-wide shadow-md shadow-sky-500/20 transition cursor-pointer"
                >
                  {ssoModal === 'google' ? 'Continue with Google' : 'Continue with GitHub'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSsoModal(null);
                    setSsoError('');
                  }}
                  className="py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium text-xs transition cursor-pointer border border-slate-700"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
